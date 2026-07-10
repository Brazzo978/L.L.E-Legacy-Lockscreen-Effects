.class public Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;
.super Ljava/lang/Object;
.source "Def.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;
    }
.end annotation


# static fields
.field public static MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

.field public static inkColorFromSettingForLCD:[[F

.field public static inkColorFromSettingForLED:[[F


# direct methods
.method static constructor <clinit>()V
    .registers 8

    .prologue
    const/4 v7, 0x4

    const/4 v6, 0x2

    const/4 v5, 0x1

    const/4 v4, 0x0

    const/4 v3, 0x3

    .line 5
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sput-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    .line 16
    const/16 v0, 0x9

    new-array v0, v0, [[F

    new-array v1, v3, [F

    fill-array-data v1, :array_9e

    aput-object v1, v0, v4

    new-array v1, v3, [F

    fill-array-data v1, :array_a8

    aput-object v1, v0, v5

    new-array v1, v3, [F

    fill-array-data v1, :array_b2

    aput-object v1, v0, v6

    new-array v1, v3, [F

    fill-array-data v1, :array_bc

    aput-object v1, v0, v3

    new-array v1, v3, [F

    fill-array-data v1, :array_c6

    aput-object v1, v0, v7

    const/4 v1, 0x5

    new-array v2, v3, [F

    fill-array-data v2, :array_d0

    aput-object v2, v0, v1

    const/4 v1, 0x6

    new-array v2, v3, [F

    fill-array-data v2, :array_da

    aput-object v2, v0, v1

    const/4 v1, 0x7

    new-array v2, v3, [F

    fill-array-data v2, :array_e4

    aput-object v2, v0, v1

    const/16 v1, 0x8

    new-array v2, v3, [F

    fill-array-data v2, :array_ee

    aput-object v2, v0, v1

    sput-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->inkColorFromSettingForLCD:[[F

    .line 28
    const/16 v0, 0x9

    new-array v0, v0, [[F

    new-array v1, v3, [F

    fill-array-data v1, :array_f8

    aput-object v1, v0, v4

    new-array v1, v3, [F

    fill-array-data v1, :array_102

    aput-object v1, v0, v5

    new-array v1, v3, [F

    fill-array-data v1, :array_10c

    aput-object v1, v0, v6

    new-array v1, v3, [F

    fill-array-data v1, :array_116

    aput-object v1, v0, v3

    new-array v1, v3, [F

    fill-array-data v1, :array_120

    aput-object v1, v0, v7

    const/4 v1, 0x5

    new-array v2, v3, [F

    fill-array-data v2, :array_12a

    aput-object v2, v0, v1

    const/4 v1, 0x6

    new-array v2, v3, [F

    fill-array-data v2, :array_134

    aput-object v2, v0, v1

    const/4 v1, 0x7

    new-array v2, v3, [F

    fill-array-data v2, :array_13e

    aput-object v2, v0, v1

    const/16 v1, 0x8

    new-array v2, v3, [F

    fill-array-data v2, :array_148

    aput-object v2, v0, v1

    sput-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->inkColorFromSettingForLED:[[F

    return-void

    .line 16
    :array_9e
    .array-data 4
        0x0
        0x3ecccccd    # 0.4f
        0x3f800000    # 1.0f
    .end array-data

    :array_a8
    .array-data 4
        0x3f52d2d3
        0x3ef0f0f1
        0x3f0c8c8d
    .end array-data

    :array_b2
    .array-data 4
        0x3f52d2d3
        0x3f0c8c8d
        0x3ea0a0a1
    .end array-data

    :array_bc
    .array-data 4
        0x3eb4b4b5
        0x3f169697
        0x3e5cdcdd
    .end array-data

    :array_c6
    .array-data 4
        0x3dc8c8c9
        0x3ea0a0a1
        0x3f39b9ba
    .end array-data

    :array_d0
    .array-data 4
        0x3dc8c8c9
        0x3e5cdcdd
        0x3ec8c8c9
    .end array-data

    :array_da
    .array-data 4
        0x3ea0a0a1
        0x3e20a0a1
        0x3f20a0a1
    .end array-data

    :array_e4
    .array-data 4
        0x3f028283
        0x3ebebebf
        0x3e5cdcdd
    .end array-data

    :array_ee
    .array-data 4
        0x3da0a0a1
        0x3ec8c8c9
        0x3f0c8c8d
    .end array-data

    .line 28
    :array_f8
    .array-data 4
        0x0
        0x3ecccccd    # 0.4f
        0x3f800000    # 1.0f
    .end array-data

    :array_102
    .array-data 4
        0x3f43c3b5
        0x3ef0f0e9
        0x3f0c8c82
    .end array-data

    :array_10c
    .array-data 4
        0x3f3ebebe
        0x3edcdcca
        0x3df0f0e9    # 0.117647f
    .end array-data

    :array_116
    .array-data 4
        0x3e8c8c72
        0x3f028273
        0x3df0f0e9    # 0.117647f
    .end array-data

    :array_120
    .array-data 4
        0x3d209fe8
        0x3eaaaa9f
        0x3f7afaf8
    .end array-data

    :array_12a
    .array-data 4
        0x0
        0x3df0f0e9    # 0.117647f
        0x3eb4b4af
    .end array-data

    :array_134
    .array-data 4
        0x3eb4b4af
        0x3e70f0e9    # 0.235294f
        0x3f34b4af
    .end array-data

    :array_13e
    .array-data 4
        0x3e5cdcca
        0x3dc8c8ac
        0x3d209fe8
    .end array-data

    :array_148
    .array-data 4
        0x3ea0a090
        0x3f20a090
        0x3f34b4af
    .end array-data
.end method

.method public constructor <init>()V
    .registers 1

    .prologue
    .line 3
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 6
    return-void
.end method
