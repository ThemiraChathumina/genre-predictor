package org.example.genreclassifier;

import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.feature.RegexTokenizer;
import org.apache.spark.ml.feature.StopWordsRemover;
import org.apache.spark.ml.feature.StringIndexerModel;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.example.genreclassifier.dto.PredictionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GenreClassifierService {

    @Value("${genre.model.path}")
    private String modelPath;

    private transient SparkSession spark;
    private transient PipelineModel model;
    private transient String[] labels;

    @PostConstruct
    public void init() {
        spark = SparkSession.builder()
                .appName("GenrePrediction")
                .master("local[*]")
                .getOrCreate();

//        File modelDir = new File(modelPath);
//        if (!modelDir.exists()) {
//            throw new RuntimeException("Model directory not found: " + modelDir.getAbsolutePath());
//        }

        model = PipelineModel.load(modelPath);
        StringIndexerModel indexerModel = (StringIndexerModel) model.stages()[2];
        labels = indexerModel.labels();  // Save label names
    }

    public String preprocessLyric(String lyric) {
        System.out.println(lyric);
        // Remove [Section Headers] like [Verse 1], [Chorus], etc.
        lyric = lyric.replaceAll("\\[.*?\\]", "").trim();

        // Step 1: Create DataFrame with one row containing the input lyric (lowercased)
        StructType schema = new StructType(new StructField[]{
                new StructField("lyrics", DataTypes.StringType, false, Metadata.empty())
        });
        Row row = RowFactory.create(lyric.toLowerCase());
        Dataset<Row> df = spark.createDataFrame(Arrays.asList(row), schema);

        // Step 2: Tokenization
        RegexTokenizer tokenizer = new RegexTokenizer()
                .setInputCol("lyrics")
                .setOutputCol("tokens")
                .setPattern("\\s+")
                .setToLowercase(true);
        Dataset<Row> tokenized = tokenizer.transform(df);

        // Step 3: Stop Words Removal
        StopWordsRemover remover = new StopWordsRemover()
                .setInputCol("tokens")
                .setOutputCol("filtered");
        Dataset<Row> cleaned = remover.transform(tokenized);

        // Step 4: Extract filtered tokens
        Row resultRow = cleaned.select("filtered").first();
        @SuppressWarnings("unchecked")
        List<String> words = resultRow.getList(0);

        // Step 5: Apply stemming using Snowball English stemmer
        SnowballStemmer stemmer = new englishStemmer();
        List<String> stemmedWords = words.stream().map(word -> {
            stemmer.setCurrent(word);
            stemmer.stem();
            return stemmer.getCurrent();
        }).collect(Collectors.toList());

        // Step 6: Return stemmed tokens as a space-separated string
        return String.join(" ", stemmedWords);
    }

    public PredictionResponse predictGenre(String lyrics) {

        String preprocessedLyrics = preprocessLyric(lyrics);
        System.out.println("Preprocessed lyrics: " + preprocessedLyrics);
        Dataset<Row> inputDF = spark.createDataFrame(
                java.util.Collections.singletonList(new LyricsInput(preprocessedLyrics)),
                LyricsInput.class
        );

        Dataset<Row> predictions = model.transform(inputDF);
        Row row = predictions.select("prediction", "probability").first();

        double predictionIndex = row.getDouble(0);
        org.apache.spark.ml.linalg.Vector probVector = row.getAs(1);

        String predictedLabel = labels[(int) predictionIndex];
        Map<String, Double> probabilities = new HashMap<>();
        for (int i = 0; i < probVector.size(); i++) {
            probabilities.put(labels[i], probVector.apply(i));
        }

        return new PredictionResponse(predictedLabel, probabilities);
    }

    // Helper bean class to hold input text
    public static class LyricsInput implements java.io.Serializable {
        private String lyrics;

        public LyricsInput(String lyrics) {
            this.lyrics = lyrics;
        }

        public String getLyrics() {
            return lyrics;
        }

        public void setLyrics(String lyrics) {
            this.lyrics = lyrics;
        }
    }
}
