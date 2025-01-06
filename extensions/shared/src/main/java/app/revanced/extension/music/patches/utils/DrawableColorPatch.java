package app.revanced.extension.music.patches.utils;

import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.apache.commons.lang3.ArrayUtils;

import app.revanced.extension.shared.utils.ResourceUtils;
import app.revanced.extension.shared.utils.Utils;

@SuppressWarnings("unused")
public class DrawableColorPatch {
    private static final int[] DARK_VALUES = {
            -14606047, // comments box background
            -16579837, // button container background in album
            -16777216, // button container background in playlist
    };

    // background colors
    private static final Drawable headerGradient =
            ResourceUtils.getDrawable("revanced_header_gradient");
    private static final int blackColor =
            ResourceUtils.getColor("yt_black1");

    public static int getLithoColor(int originalValue) {
        return ArrayUtils.contains(DARK_VALUES, originalValue)
                ? blackColor
                : originalValue;
    }

    public static void setHeaderGradient(ViewGroup viewGroup) {
        viewGroup.getViewTreeObserver().addOnGlobalLayoutListener(() -> Utils.runOnMainThreadDelayed(() -> {
            if (!(viewGroup.getChildAt(0) instanceof ViewGroup parentViewGroup))
                return;
            if (!(parentViewGroup.getChildAt(0) instanceof ImageView gradientView))
                return;
            if (headerGradient != null) {
                gradientView.setForeground(headerGradient);
            }
        }, 0));
    }
}


