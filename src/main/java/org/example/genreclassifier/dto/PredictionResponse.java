package org.example.genreclassifier.dto;

import java.util.Map;

public class PredictionResponse {
    private String predictedLabel;
    private Map<String, Double> probabilities;

    public PredictionResponse(String predictedLabel, Map<String, Double> probabilities) {
        this.predictedLabel = predictedLabel;
        this.probabilities = probabilities;
    }

    public String getPredictedLabel() {
        return predictedLabel;
    }

    public Map<String, Double> getProbabilities() {
        return probabilities;
    }
}
