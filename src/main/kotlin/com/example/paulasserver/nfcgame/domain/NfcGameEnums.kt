package com.example.paulasserver.nfcgame.domain

enum class CardType {
    PLAYER,
    GAME,
    UNKNOWN,
}

enum class CardStatus {
    UNASSIGNED,
    ASSIGNED,
    DISABLED,
}

enum class SessionStatus {
    LOBBY,
    CONFIGURING,
    BUILDING_TEAMS,
    READY,
    RUNNING,
    FINISHED,
    RESET,
    CANCELLED,
}

enum class EventType {
    CARD_SCANNED,
    GAME_CARD_SCANNED,
    PLAYER_CARD_SCANNED,
    TOUCH_MENU_SELECT,
    TOUCH_NUMBER_SET,
    TOUCH_CONFIRM,
    JOYSTICK_LEFT,
    JOYSTICK_RIGHT,
    JOYSTICK_UP,
    JOYSTICK_DOWN,
    JOYSTICK_PRESS,
    JOYSTICK_LONG_PRESS,
    RESET_TRIGGERED,
}

enum class ScreenType {
    MESSAGE,
    MENU,
    NUMBER_PICKER,
    WAITING_FOR_SCAN,
    TEAM_OVERVIEW,
    BANKING_TRANSFER,
    RESULT,
    ERROR,
}

enum class OwnerType {
    TEAM,
    BANK,
}

enum class WinRuleType {
    FIRST_TO_WIN,
    MOST_POINTS_AFTER_ROUNDS,
    ROUND_WIN,
    MANUAL,
}

enum class RoundLimitType {
    NONE,
    ROUNDS,
    POINTS,
}

enum class AdminRole {
    ROLE_ADMIN,
}

enum class GamePublicationStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED,
}
