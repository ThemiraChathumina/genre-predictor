package org.example.genreclassifier;

import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.ml.classification.LogisticRegression;
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator;
import org.apache.spark.ml.feature.RegexTokenizer;
import org.apache.spark.ml.feature.StringIndexer;
import org.apache.spark.ml.feature.Word2Vec;
import org.apache.spark.sql.*;
import org.apache.spark.sql.expressions.Window;
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.spark.sql.functions.*;

public class TrainModel {

    public static class LyricData implements Serializable {
        private String lyrics;
        private String genre;

        public LyricData() {}

        public LyricData(String lyrics, String genre) {
            this.lyrics = lyrics;
            this.genre = genre;
        }

        public String getLyrics() { return lyrics; }
        public void setLyrics(String lyrics) { this.lyrics = lyrics; }
        public String getGenre() { return genre; }
        public void setGenre(String genre) { this.genre = genre; }
    }

    public static void Train() throws IOException {
        SparkSession spark = SparkSession.builder()
                .appName("GenreClassificationPipelineJava")
                .master("local[*]")
                .getOrCreate();

        Dataset<Row> df = spark.read()
                .option("header", "true")
                .option("inferSchema", "true")
                .csv("C:\\Users\\chath\\Documents\\academics sem 8\\big data\\music\\genre-predictor\\merged_dataset.csv")
                .filter(col("lyrics").isNotNull().and(col("genre").isNotNull()));

        Dataset<LyricData> processedDS = df.map((MapFunction<Row, LyricData>) row -> {
            String lyrics = row.getAs("lyrics");
            String genre = row.getAs("genre");
            lyrics = lyrics.replaceAll("\\[.*?\\]", "").trim().toLowerCase();
            List<String> tokens = Arrays.asList(lyrics.split("\\s+"));
            List<String> stopwords = Arrays.asList(org.apache.spark.ml.feature.StopWordsRemover.loadDefaultStopWords("english"));
            List<String> filtered = tokens.stream().filter(word -> !stopwords.contains(word)).collect(Collectors.toList());
            SnowballStemmer stemmer = new englishStemmer();
            List<String> stemmed = filtered.stream().map(word -> {
                stemmer.setCurrent(word);
                stemmer.stem();
                return stemmer.getCurrent();
            }).collect(Collectors.toList());
            return new LyricData(String.join(" ", stemmed), genre);
        }, Encoders.bean(LyricData.class));

        Dataset<Row> finalDF = processedDS.toDF();

        RegexTokenizer tokenizer = new RegexTokenizer().setInputCol("lyrics").setOutputCol("tokens").setPattern("\\s+");
        Word2Vec word2Vec = new Word2Vec().setInputCol("tokens").setOutputCol("features").setVectorSize(500).setMinCount(5).setWindowSize(5).setMaxIter(10).setStepSize(0.025).setSeed(42);
        StringIndexer labelIndexer = new StringIndexer().setInputCol("genre").setOutputCol("label");
        LogisticRegression lr = new LogisticRegression().setMaxIter(20).setRegParam(0.1);

        Pipeline pipeline = new Pipeline().setStages(new PipelineStage[]{tokenizer, word2Vec, labelIndexer, lr});

        Dataset<Row>[] splits = finalDF.randomSplit(new double[]{0.8, 0.2}, 42);
        Dataset<Row> trainDF = splits[0];
        Dataset<Row> testDF = splits[1];

        PipelineModel model = pipeline.fit(trainDF);
        Dataset<Row> predictions = model.transform(testDF);

        MulticlassClassificationEvaluator accEval = new MulticlassClassificationEvaluator().setLabelCol("label").setPredictionCol("prediction").setMetricName("accuracy");
        MulticlassClassificationEvaluator f1Eval = new MulticlassClassificationEvaluator().setLabelCol("label").setPredictionCol("prediction").setMetricName("f1");

        System.out.printf("Accuracy: %.4f\n", accEval.evaluate(predictions));
        System.out.printf("F1 Score: %.4f\n", f1Eval.evaluate(predictions));

        System.out.println("Confusion Matrix:");
        predictions.groupBy("label", "prediction").count().orderBy("label", "prediction").show();

        // Show 10 correctly classified lyrics per genre
        Dataset<Row> correct = predictions.filter(col("label").equalTo(col("prediction")));
        Dataset<Row> withIndex = correct.withColumn("row_number", row_number().over(Window.partitionBy("label").orderBy(rand())));
        Dataset<Row> top10PerLabel = withIndex.filter(col("row_number").leq(10));
        top10PerLabel.select("genre", "lyrics").orderBy("genre").show(100, false);

        model.write().overwrite().save("C:\\Users\\chath\\Documents\\academics sem 8\\big data\\music\\genre-predictor\\build\\libs\\genre_classifier_model");
        System.out.println("Model saved.");
        spark.stop();
    }
}
