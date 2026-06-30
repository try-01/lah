package com.tvhanan.domain.model

enum class RemoteKey(val keyCode: String, val label: String) {
    POWER("KEY_POWER", "Power"),
    HOME("KEY_HOME", "Home"),
    BACK("KEY_RETURN", "Back"),

    DPAD_UP("KEY_UP", "\u25B2"),
    DPAD_DOWN("KEY_DOWN", "\u25BC"),
    DPAD_LEFT("KEY_LEFT", "\u25C0"),
    DPAD_RIGHT("KEY_RIGHT", "\u25B6"),
    ENTER("KEY_ENTER", "OK"),

    VOL_UP("KEY_VOLUP", "Vol+"),
    VOL_DOWN("KEY_VOLDOWN", "Vol-"),
    MUTE("KEY_MUTE", "Mute"),

    CH_UP("KEY_CHUP", "CH+"),
    CH_DOWN("KEY_CHDOWN", "CH-"),
    CH_LIST("KEY_CH_LIST", "CH List"),

    KEY_0("KEY_0", "0"),
    KEY_1("KEY_1", "1"),
    KEY_2("KEY_2", "2"),
    KEY_3("KEY_3", "3"),
    KEY_4("KEY_4", "4"),
    KEY_5("KEY_5", "5"),
    KEY_6("KEY_6", "6"),
    KEY_7("KEY_7", "7"),
    KEY_8("KEY_8", "8"),
    KEY_9("KEY_9", "9"),
    PRE_CH("KEY_PRECH", "Pre-CH"),

    RED("KEY_RED", "Red"),
    GREEN("KEY_GREEN", "Green"),
    YELLOW("KEY_YELLOW", "Yellow"),
    BLUE("KEY_BLUE", "Blue"),

    SOURCE("KEY_SOURCE", "Source"),
    HDMI("KEY_HDMI", "HDMI"),

    PLAY("KEY_PLAY", "Play"),
    PAUSE("KEY_PAUSE", "Pause"),
    STOP("KEY_STOP", "Stop"),
    REWIND("KEY_REWIND", "Rewind"),
    FAST_FORWARD("KEY_FF", "FF"),

    MENU("KEY_MENU", "Menu"),
    GUIDE("KEY_GUIDE", "Guide"),
    INFO("KEY_INFO", "Info"),
    EXIT("KEY_EXIT", "Exit")
}
