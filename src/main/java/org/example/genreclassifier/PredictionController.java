package org.example.genreclassifier;

import org.example.genreclassifier.dto.PredictionRequest;
import org.example.genreclassifier.dto.PredictionResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/predict")
@CrossOrigin // optional: enable if frontend runs on different port
public class PredictionController {

    @Autowired
    private GenreClassifierService classifierService;

    @PostMapping
    public PredictionResponse predict(@RequestBody PredictionRequest request) {
        return classifierService.predictGenre(request.getLyrics());
    }
}
