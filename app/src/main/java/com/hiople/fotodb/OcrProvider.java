package com.hiople.fotodb;

import android.graphics.Bitmap;

interface OcrProvider {
    void recognize(Bitmap bitmap, Callback callback);

    void close();

    interface Callback {
        void onSuccess(String text);

        void onFailure(Exception error);

        void onComplete();
    }
}
