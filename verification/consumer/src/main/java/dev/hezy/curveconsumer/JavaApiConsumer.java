package dev.hezy.curveconsumer;

import java.util.Arrays;
import java.util.Collections;
import dev.hezy.curveview.CustomCurveView;

public final class JavaApiConsumer {
    public static void exercise(CustomCurveView curve) {
        curve.setStyle(curve.getStyle());
        CustomCurveView.ValueFormatter formatter = value -> Float.toString(value) + " C";
        curve.setValueFormatter(formatter);
        CustomCurveView.OnSelectionChangedListener listener = curve.getOnSelectionChangedListener();
        curve.setOnSelectionChangedListener(listener);
        curve.setDrawableSize(24f);
        curve.setValueRange(-10f, 10f);
        curve.clearValueRange();
        curve.setData(Arrays.asList(-2f, 4f, 1f));
        curve.setData(Arrays.asList(-2f, 4f, 1f), Collections.singletonMap(1, android.R.drawable.ic_menu_compass));
        curve.setData(Arrays.asList(-2f, 4f, 1f), Collections.emptyMap(), Arrays.asList("06:00", "12:00", "18:00"));
        curve.setCurrentIndex(-1);
        if (curve.getSelectedValue() != null || curve.getSelectedIndex() != -1) {
            throw new AssertionError("Java selection clear");
        }
        curve.setCurrentIndex(1);
        if (curve.getPointCount() != 3 || curve.getSelectedIndex() != 1 || !Float.valueOf(4f).equals(curve.getSelectedValue())) {
            throw new AssertionError("Java selection API");
        }
    }
}
