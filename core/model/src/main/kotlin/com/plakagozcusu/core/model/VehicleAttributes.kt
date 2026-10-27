package com.plakagozcusu.core.model

enum class VehicleClass { CAR, VAN, TRUCK, BUS, MOTORCYCLE, UNKNOWN }

/** Motion relative to the image, not physical speed (pixel displacement is not km/h). */
enum class MotionStatus { MOVING, STATIONARY, UNKNOWN }

/** Direction of travel in image space as seen from the mounted phone. */
enum class TravelDirection { TOWARD_CAMERA, AWAY_FROM_CAMERA, LEFT_TO_RIGHT, RIGHT_TO_LEFT, UNKNOWN }
