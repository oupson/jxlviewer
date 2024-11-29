//
// Created by oupson on 29/11/2024.
//

#ifndef JXLVIEWER_OPTIONS_H
#define JXLVIEWER_OPTIONS_H
enum BitmapConfig {
    RGBA_8888 = 0, F16 = 1,
};

class Options {
public:
    BitmapConfig rgbaConfig = RGBA_8888;
    bool decodeMultipleFrames = true;
};

#endif //JXLVIEWER_OPTIONS_H
