package com.example.ui

enum class VehicleProfile(
    val id: String,
    val label: String,
    val description: String,
    val maxRpm: Float
) {
    V8_MUSCLE(
        id = "v8_muscle",
        label = "Classic V8 Muscle",
        description = "Yüksek hacimli, derin homurtusu ve yüksek torklu karakteristiği ile efsanevi V8 motoru.",
        maxRpm = 6500f
    ),
    DODGE_DEMON_V8(
        id = "dodge_demon_v8",
        label = "Dodge Demon Supercharged",
        description = "6.2L Supercharged V8 motoru ve kompresör tahrikli yüksek devir canavarı.",
        maxRpm = 7200f
    ),
    V10_RACING(
        id = "v10_racing",
        label = "V10 Formula Rev",
        description = "Yüksek devirli, keskin ve metalik çığlığı ile eski nesil F1 yarış motorlarından esinlenildi.",
        maxRpm = 12000f
    ),
    SUPRA_2JZ_GTR(
        id = "supra_2jz_gtr",
        label = "Legendary 2JZ & RB26",
        description = "Sıralı 6 silindir yapısıyla pürüzsüz ve yüksek devirli twin-turbo efsanesi.",
        maxRpm = 8500f
    ),
    RALLY_GROUP_B(
        id = "rally_group_b",
        label = "Rally Spec Group-B",
        description = "Limitlerde çalışan 4 silindirli, turbo beslemeli safkan ralli makinesi.",
        maxRpm = 8200f
    ),
    ROTARY_WANKEL(
        id = "rotary_wankel",
        label = "Rotary RX-8 Renesis",
        description = "Çift rotorlu Wankel motorunun karakteristik yüksek devir feryadı.",
        maxRpm = 10000f
    ),
    V12_SUPERCAR(
        id = "v12_supercar",
        label = "Symphonic V12 Exotic",
        description = "Orkestral kalitede tasarlanmış, akıl almaz keskin tınılı egzotik V12 motoru.",
        maxRpm = 9500f
    ),
    ELECTRIC_TURBINE(
        id = "electric_turbine",
        label = "Sci-Fi Electric Turbine",
        description = "Fütüristik elektrikli araç motoru ve yüksek vınıltılı dişli kutusu simülasyonu.",
        maxRpm = 10000f
    )
}
