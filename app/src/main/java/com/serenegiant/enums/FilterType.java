package com.serenegiant.enums;

import android.support.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static com.serenegiant.enums.FilterType.ART;
import static com.serenegiant.enums.FilterType.BLOOM;
import static com.serenegiant.enums.FilterType.COLOR_INVERT;
import static com.serenegiant.enums.FilterType.F1977;
import static com.serenegiant.enums.FilterType.GRAY_SCALE;
import static com.serenegiant.enums.FilterType.NONE;
import static com.serenegiant.enums.FilterType.POSTERIZE;

/**
 * Created by Pankaj Soni <pankajsoni@softwarejoint.com> on 02/03/18.
 * Copyright (c) 2018 Software Joint. All rights reserved.
 */
@Retention(RetentionPolicy.SOURCE)
@StringDef({NONE, F1977, ART, COLOR_INVERT, GRAY_SCALE, POSTERIZE, BLOOM})
public @interface FilterType {
    String NONE = "none";
    String F1977 = "F1977";
    String ART = "art";
    String COLOR_INVERT = "colorInvert";
    String GRAY_SCALE = "grayscale";
    String POSTERIZE = "posterize";
    String BLOOM = "bloom";
}