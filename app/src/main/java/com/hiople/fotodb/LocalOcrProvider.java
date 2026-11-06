package com.hiople.fotodb;

import android.graphics.Bitmap;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

final class LocalOcrProvider implements OcrProvider {
    private final TextRecognizer recognizer = TextRecognition.getClient(
            new ChineseTextRecognizerOptions.Builder().build());

    @Override
    public void recognize(Bitmap bitmap, Callback callback) {
        final Task<Text> task;
        try {
            task = recognizer.process(InputImage.fromBitmap(bitmap, 0));
        } catch (RuntimeException error) {
            callback.onFailure(error);
            callback.onComplete();
            return;
        }
        task.addOnSuccessListener(result -> callback.onSuccess(result.getText()))
                .addOnFailureListener(callback::onFailure)
                .addOnCompleteListener(ignored -> callback.onComplete());
    }

    @Override
    public void close() {
        recognizer.close();
    }
}
