.class public Lcom/android/keyguard/sec/MassRippleUnlockTwin;
.super Landroid/widget/FrameLayout;
.source "MassRippleUnlockTwin.java"

# interfaces
.implements Lcom/android/keyguard/sec/KeyguardEffectViewBase;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;,
        Lcom/android/keyguard/sec/MassRippleUnlockTwin$SoundPoolThread;
    }
.end annotation


# static fields
.field protected static final TAG:Ljava/lang/String; = "MassRippleUnlockTwin"

.field private static final mRDownPath:Ljava/lang/String; = "/system/media/audio/ui/simple_ripple_down.ogg"

.field private static final mRUpPath:Ljava/lang/String; = "/system/media/audio/ui/simple_ripple_up.ogg"


# instance fields
.field private final ACQUIRE_DVFS:I

.field protected final ANIMATION_DURATION:F

.field final CIRCLE_AFFORDANCE_ROUND_SIZE:F

.field final CIRCLE_ROUND_SIZE:F

.field private final COUNT_ANIMATION:I

.field private CPU_CLOCK_NUM:I

.field private final CPU_CLOK_CONTROL:I

.field private DEBUG:Z

.field private GPU_FREQUNCY_NUM:I

.field private final GPU_FREQ_CONTROL:I

.field final MAX_RIPPLE_COUNT:I

.field final MSG_AFFORDANCE_TOUCH:I

.field final MSG_FIRST_TOUCH:I

.field final MSG_TIME_TICK:I

.field private final RELEASE_DVFS:I

.field final SOUND_ID_DOWN:I

.field final SOUND_ID_UP:I

.field private TIME_FOR_CPU_GPU_MAX_LOCK:I

.field protected final UNLOCK_DRAG_THRESHOLD:D

.field protected final UNLOCK_RELEASE_THRESHOLD:D

.field cpuMaxClockBooster:Landroid/os/DVFSHelper;

.field private diffPressTime:J

.field drawRippleCount:I

.field firstTouch_X:I

.field firstTouch_Y:I

.field gpuMaxFreqBooster:Landroid/os/DVFSHelper;

.field private indexAni:I

.field private isRestrictCPUClock:Z

.field private isRestrictGPUFreq:Z

.field private isStartUnlock:Z

.field private isSystemSoundChecked:Z

.field protected mCircleMain:Landroid/widget/FrameLayout;

.field private mContext:Landroid/content/Context;

.field private mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

.field private mDistanceRatio:D

.field mHandler:Landroid/os/Handler;

.field mMovingRippleCount:I

.field private mRDownId:I

.field private mRUpId:I

.field private mSoundPool:Landroid/media/SoundPool;

.field private massRipple:[Landroid/widget/ImageView;

.field prevMovingDistance:F

.field private prevPressTime:J

.field scale:[Landroid/view/animation/Animation;

.field private soundNum:I

.field private soundTime:I

.field private sounds:[I

.field supportedCPUClockTable:[I

.field supportedGPUFreqTable:[I

.field touchedEventX:F

.field touchedEventY:F

.field typeStorke:[F


# direct methods
.method public constructor <init>(Landroid/content/Context;)V
    .registers 11
    .param p1, "context"    # Landroid/content/Context;

    .prologue
    const/4 v8, 0x6

    const/4 v7, -0x1

    const/4 v6, 0x0

    const/4 v5, 0x1

    const/4 v4, 0x0

    .line 119
    invoke-direct {p0, p1}, Landroid/widget/FrameLayout;-><init>(Landroid/content/Context;)V

    .line 48
    iput-boolean v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->DEBUG:Z

    .line 51
    iput v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    .line 52
    const/4 v2, 0x3

    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->COUNT_ANIMATION:I

    .line 54
    new-array v2, v8, [Landroid/view/animation/Animation;

    iput-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->scale:[Landroid/view/animation/Animation;

    .line 56
    const-wide v2, 0x3fe99999a0000000L    # 0.800000011920929

    iput-wide v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->UNLOCK_RELEASE_THRESHOLD:D

    .line 57
    const-wide v2, 0x3ff4ccccc0000000L    # 1.2999999523162842

    iput-wide v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->UNLOCK_DRAG_THRESHOLD:D

    .line 59
    const v2, 0x44a28000    # 1300.0f

    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->ANIMATION_DURATION:F

    .line 63
    iput v7, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_X:I

    .line 64
    iput v7, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_Y:I

    .line 66
    const/4 v2, 0x0

    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->prevMovingDistance:F

    .line 74
    iput v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->drawRippleCount:I

    .line 79
    const/4 v2, 0x4

    new-array v2, v2, [F

    fill-array-data v2, :array_c0

    iput-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->typeStorke:[F

    .line 82
    const/high16 v2, 0x43910000    # 290.0f

    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->CIRCLE_ROUND_SIZE:F

    .line 83
    const/high16 v2, 0x43600000    # 224.0f

    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->CIRCLE_AFFORDANCE_ROUND_SIZE:F

    .line 85
    iput v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->MSG_FIRST_TOUCH:I

    .line 86
    iput v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->MSG_AFFORDANCE_TOUCH:I

    .line 88
    iput v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->MSG_TIME_TICK:I

    .line 89
    const/4 v2, 0x2

    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->MAX_RIPPLE_COUNT:I

    .line 91
    iput v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mMovingRippleCount:I

    .line 94
    iput-boolean v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isStartUnlock:Z

    .line 97
    iput v7, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->CPU_CLOCK_NUM:I

    .line 98
    iput-object v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    .line 99
    iput-object v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedCPUClockTable:[I

    .line 100
    iput-boolean v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictCPUClock:Z

    .line 103
    iput v7, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->GPU_FREQUNCY_NUM:I

    .line 104
    iput-object v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    .line 105
    iput-object v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedGPUFreqTable:[I

    .line 106
    iput-boolean v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictGPUFreq:Z

    .line 108
    iput v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->CPU_CLOK_CONTROL:I

    .line 109
    iput v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->GPU_FREQ_CONTROL:I

    .line 111
    const v2, 0x88b8

    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->TIME_FOR_CPU_GPU_MAX_LOCK:I

    .line 113
    iput v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->ACQUIRE_DVFS:I

    .line 114
    iput v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->RELEASE_DVFS:I

    .line 116
    iput-object v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    .line 159
    new-instance v2, Lcom/android/keyguard/sec/MassRippleUnlockTwin$1;

    invoke-direct {v2, p0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin$1;-><init>(Lcom/android/keyguard/sec/MassRippleUnlockTwin;)V

    iput-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mHandler:Landroid/os/Handler;

    .line 534
    iput-object v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    .line 535
    iput-object v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    .line 536
    const-wide/16 v2, 0x0

    iput-wide v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->diffPressTime:J

    .line 537
    const-wide/16 v2, 0x0

    iput-wide v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->prevPressTime:J

    .line 538
    const/4 v2, 0x5

    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->soundNum:I

    .line 539
    iput v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->soundTime:I

    .line 540
    iput v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->SOUND_ID_DOWN:I

    .line 541
    iput v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->SOUND_ID_UP:I

    .line 542
    sget v2, Lcom/android/keyguard/R$raw;->simple_ripple_down:I

    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mRDownId:I

    .line 543
    sget v2, Lcom/android/keyguard/R$raw;->simple_ripple_up:I

    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mRUpId:I

    .line 547
    iput-boolean v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isSystemSoundChecked:Z

    .line 121
    iput-object p1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    .line 123
    const-string v2, "ro.product.name"

    invoke-static {v2}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 124
    .local v1, "productName":Ljava/lang/String;
    if-eqz v1, :cond_ac

    const-string v2, "lentislte"

    invoke-virtual {v1, v2}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v2

    if-eqz v2, :cond_ac

    .line 125
    const v2, 0x180600

    const v3, 0x11e1a300

    invoke-direct {p0, v5, v2, v5, v3}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->setValueOfDVFS(ZIZI)V

    .line 128
    :cond_ac
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    invoke-static {v2}, Landroid/view/LayoutInflater;->from(Landroid/content/Context;)Landroid/view/LayoutInflater;

    move-result-object v0

    .line 129
    .local v0, "inflater":Landroid/view/LayoutInflater;
    sget v2, Lcom/android/keyguard/R$layout;->keyguard_mass_ripple_effect_view:I

    invoke-virtual {v0, v2, p0, v5}, Landroid/view/LayoutInflater;->inflate(ILandroid/view/ViewGroup;Z)Landroid/view/View;

    .line 131
    new-array v2, v8, [Landroid/widget/ImageView;

    iput-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    .line 132
    invoke-direct {p0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->setLayout()V

    .line 134
    return-void

    .line 79
    nop

    :array_c0
    .array-data 4
        0x42440000    # 49.0f
        0x41d4cccd    # 26.6f
        0x42140000    # 37.0f
        0x41f00000    # 30.0f
    .end array-data
.end method

.method static synthetic access$000(Lcom/android/keyguard/sec/MassRippleUnlockTwin;)I
    .registers 2
    .param p0, "x0"    # Lcom/android/keyguard/sec/MassRippleUnlockTwin;

    .prologue
    .line 44
    iget v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->soundNum:I

    return v0
.end method

.method static synthetic access$100(Lcom/android/keyguard/sec/MassRippleUnlockTwin;)Z
    .registers 2
    .param p0, "x0"    # Lcom/android/keyguard/sec/MassRippleUnlockTwin;

    .prologue
    .line 44
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isSystemSoundChecked:Z

    return v0
.end method

.method static synthetic access$200(Lcom/android/keyguard/sec/MassRippleUnlockTwin;)Landroid/media/SoundPool;
    .registers 2
    .param p0, "x0"    # Lcom/android/keyguard/sec/MassRippleUnlockTwin;

    .prologue
    .line 44
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    return-object v0
.end method

.method static synthetic access$300(Lcom/android/keyguard/sec/MassRippleUnlockTwin;)I
    .registers 2
    .param p0, "x0"    # Lcom/android/keyguard/sec/MassRippleUnlockTwin;

    .prologue
    .line 44
    iget v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->soundTime:I

    return v0
.end method

.method static synthetic access$400(Lcom/android/keyguard/sec/MassRippleUnlockTwin;)V
    .registers 1
    .param p0, "x0"    # Lcom/android/keyguard/sec/MassRippleUnlockTwin;

    .prologue
    .line 44
    invoke-direct {p0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->aquireCpuGpuMaxLock()V

    return-void
.end method

.method static synthetic access$500(Lcom/android/keyguard/sec/MassRippleUnlockTwin;)V
    .registers 1
    .param p0, "x0"    # Lcom/android/keyguard/sec/MassRippleUnlockTwin;

    .prologue
    .line 44
    invoke-direct {p0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->releaseCpuGpuMaxLock()V

    return-void
.end method

.method private acquireBooster(I)V
    .registers 8
    .param p1, "type"    # I

    .prologue
    .line 727
    const/4 v0, 0x0

    .line 728
    .local v0, "bestCpuClock":I
    const/4 v1, 0x0

    .line 730
    .local v1, "bestGpuFreq":I
    if-nez p1, :cond_56

    .line 732
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    if-eqz v2, :cond_55

    .line 734
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedCPUClockTable:[I

    if-nez v2, :cond_55

    .line 736
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    invoke-virtual {v2}, Landroid/os/DVFSHelper;->getSupportedCPUFrequency()[I

    move-result-object v2

    iput-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedCPUClockTable:[I

    .line 738
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedCPUClockTable:[I

    if-eqz v2, :cond_55

    .line 740
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedCPUClockTable:[I

    iget v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->CPU_CLOCK_NUM:I

    invoke-direct {p0, v2, v3}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->getBestMaxFreq([II)I

    move-result v0

    .line 741
    const-string v2, "MassRippleUnlockTwin"

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "== DVFS == acquire!!! CPU, ["

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    iget-object v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedCPUClockTable:[I

    aget v4, v4, v0

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v4, "]"

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 742
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    const-string v3, "CPU"

    iget-object v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedCPUClockTable:[I

    aget v4, v4, v0

    int-to-long v4, v4

    invoke-virtual {v2, v3, v4, v5}, Landroid/os/DVFSHelper;->addExtraOption(Ljava/lang/String;J)V

    .line 743
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    iget v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->TIME_FOR_CPU_GPU_MAX_LOCK:I

    invoke-virtual {v2, v3}, Landroid/os/DVFSHelper;->acquire(I)V

    .line 766
    :cond_55
    :goto_55
    return-void

    .line 750
    :cond_56
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    if-eqz v2, :cond_55

    .line 752
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedGPUFreqTable:[I

    if-nez v2, :cond_55

    .line 754
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    invoke-virtual {v2}, Landroid/os/DVFSHelper;->getSupportedGPUFrequency()[I

    move-result-object v2

    iput-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedGPUFreqTable:[I

    .line 756
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedGPUFreqTable:[I

    if-eqz v2, :cond_55

    .line 758
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedGPUFreqTable:[I

    iget v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->GPU_FREQUNCY_NUM:I

    invoke-direct {p0, v2, v3}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->getBestMaxFreq([II)I

    move-result v1

    .line 759
    const-string v2, "MassRippleUnlockTwin"

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "== DVFS == acquire!!! GPU, ["

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    iget-object v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedGPUFreqTable:[I

    aget v4, v4, v1

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v4, "]"

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 760
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    const-string v3, "GPU"

    iget-object v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedGPUFreqTable:[I

    aget v4, v4, v1

    int-to-long v4, v4

    invoke-virtual {v2, v3, v4, v5}, Landroid/os/DVFSHelper;->addExtraOption(Ljava/lang/String;J)V

    .line 761
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    iget v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->TIME_FOR_CPU_GPU_MAX_LOCK:I

    invoke-virtual {v2, v3}, Landroid/os/DVFSHelper;->acquire(I)V

    goto :goto_55
.end method

.method private aquireCpuGpuMaxLock()V
    .registers 2

    .prologue
    .line 705
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictCPUClock:Z

    if-eqz v0, :cond_8

    .line 706
    const/4 v0, 0x0

    invoke-direct {p0, v0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->acquireBooster(I)V

    .line 709
    :cond_8
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_10

    .line 710
    const/4 v0, 0x1

    invoke-direct {p0, v0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->acquireBooster(I)V

    .line 712
    :cond_10
    return-void
.end method

.method private checkSound()V
    .registers 6

    .prologue
    const/4 v4, 0x1

    .line 552
    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    invoke-virtual {v3}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    .line 553
    .local v0, "cr":Landroid/content/ContentResolver;
    const/4 v2, 0x0

    .line 557
    .local v2, "result":I
    :try_start_8
    const-string v3, "lockscreen_sounds_enabled"

    invoke-static {v0, v3}, Landroid/provider/Settings$System;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;)I
    :try_end_d
    .catch Landroid/provider/Settings$SettingNotFoundException; {:try_start_8 .. :try_end_d} :catch_13

    move-result v2

    .line 565
    :goto_e
    if-ne v2, v4, :cond_18

    .line 566
    iput-boolean v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isSystemSoundChecked:Z

    .line 570
    :goto_12
    return-void

    .line 559
    :catch_13
    move-exception v1

    .line 561
    .local v1, "e":Landroid/provider/Settings$SettingNotFoundException;
    invoke-virtual {v1}, Landroid/provider/Settings$SettingNotFoundException;->printStackTrace()V

    goto :goto_e

    .line 568
    .end local v1    # "e":Landroid/provider/Settings$SettingNotFoundException;
    :cond_18
    const/4 v3, 0x0

    iput-boolean v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isSystemSoundChecked:Z

    goto :goto_12
.end method

.method private clearAllViews()V
    .registers 4

    .prologue
    .line 408
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    const/4 v2, 0x4

    invoke-virtual {v1, v2}, Landroid/widget/FrameLayout;->setVisibility(I)V

    .line 410
    const/4 v0, 0x0

    .local v0, "i":I
    :goto_7
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    array-length v1, v1

    if-ge v0, v1, :cond_33

    .line 411
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    aget-object v1, v1, v0

    if-eqz v1, :cond_30

    .line 412
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    aget-object v1, v1, v0

    invoke-virtual {v1}, Landroid/widget/ImageView;->clearAnimation()V

    .line 413
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    aget-object v1, v1, v0

    const/16 v2, 0x8

    invoke-virtual {v1, v2}, Landroid/widget/ImageView;->setVisibility(I)V

    .line 414
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    aget-object v2, v2, v0

    invoke-virtual {v1, v2}, Landroid/widget/FrameLayout;->removeView(Landroid/view/View;)V

    .line 415
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    const/4 v2, 0x0

    aput-object v2, v1, v0

    .line 410
    :cond_30
    add-int/lit8 v0, v0, 0x1

    goto :goto_7

    .line 418
    :cond_33
    return-void
.end method

.method private getBestMaxFreq([II)I
    .registers 9
    .param p1, "pArray"    # [I
    .param p2, "bestValue"    # I

    .prologue
    .line 800
    const/4 v4, 0x0

    .line 801
    .local v4, "value":I
    const v3, 0x7fffffff

    .line 802
    .local v3, "prevdiff":I
    const/4 v1, 0x0

    .line 803
    .local v1, "currdiff":I
    array-length v0, p1

    .line 805
    .local v0, "arrayLenth":I
    const/4 v2, 0x0

    .local v2, "i":I
    :goto_7
    if-ge v2, v0, :cond_18

    .line 807
    aget v5, p1, v2

    sub-int v5, p2, v5

    invoke-static {v5}, Ljava/lang/Math;->abs(I)I

    move-result v1

    .line 808
    if-ge v1, v3, :cond_15

    .line 810
    move v4, v2

    .line 811
    move v3, v1

    .line 805
    :cond_15
    add-int/lit8 v2, v2, 0x1

    goto :goto_7

    .line 815
    :cond_18
    return v4
.end method

.method private playDragSound(I)V
    .registers 11
    .param p1, "soundId"    # I

    .prologue
    const/4 v4, 0x0

    const/high16 v2, 0x3f800000    # 1.0f

    .line 606
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isStartUnlock:Z

    if-eqz v0, :cond_8

    .line 615
    :cond_7
    :goto_7
    return-void

    .line 609
    :cond_8
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isSystemSoundChecked:Z

    if-eqz v0, :cond_7

    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    if-eqz v0, :cond_7

    .line 610
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    aget v1, v1, p1

    move v3, v2

    move v5, v4

    move v6, v2

    invoke-virtual/range {v0 .. v6}, Landroid/media/SoundPool;->play(IFFIIF)I

    move-result v8

    .line 611
    .local v8, "streanID":I
    add-int/lit8 v8, v8, -0x1

    .line 612
    new-instance v7, Lcom/android/keyguard/sec/MassRippleUnlockTwin$SoundPoolThread;

    invoke-direct {v7, p0, v8}, Lcom/android/keyguard/sec/MassRippleUnlockTwin$SoundPoolThread;-><init>(Lcom/android/keyguard/sec/MassRippleUnlockTwin;I)V

    .line 613
    .local v7, "soundpoolThread":Lcom/android/keyguard/sec/MassRippleUnlockTwin$SoundPoolThread;
    invoke-virtual {v7}, Lcom/android/keyguard/sec/MassRippleUnlockTwin$SoundPoolThread;->run()V

    goto :goto_7
.end method

.method private playSound(I)V
    .registers 9
    .param p1, "soundId"    # I

    .prologue
    const/4 v4, 0x0

    const/high16 v2, 0x3f800000    # 1.0f

    .line 596
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isStartUnlock:Z

    if-eqz v0, :cond_8

    .line 602
    :cond_7
    :goto_7
    return-void

    .line 599
    :cond_8
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isSystemSoundChecked:Z

    if-eqz v0, :cond_7

    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    if-eqz v0, :cond_7

    .line 600
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    aget v1, v1, p1

    move v3, v2

    move v5, v4

    move v6, v2

    invoke-virtual/range {v0 .. v6}, Landroid/media/SoundPool;->play(IFFIIF)I

    goto :goto_7
.end method

.method private releaseCpuGpuMaxLock()V
    .registers 2

    .prologue
    .line 716
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictCPUClock:Z

    if-eqz v0, :cond_8

    .line 717
    const/4 v0, 0x0

    invoke-virtual {p0, v0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->releaseBooster(I)V

    .line 720
    :cond_8
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_10

    .line 721
    const/4 v0, 0x1

    invoke-virtual {p0, v0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->releaseBooster(I)V

    .line 723
    :cond_10
    return-void
.end method

.method private setLayout()V
    .registers 6

    .prologue
    .line 138
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->scale:[Landroid/view/animation/Animation;

    const/4 v2, 0x0

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    sget v4, Lcom/android/keyguard/R$anim;->scale_1:I

    invoke-static {v3, v4}, Landroid/view/animation/AnimationUtils;->loadAnimation(Landroid/content/Context;I)Landroid/view/animation/Animation;

    move-result-object v3

    aput-object v3, v1, v2

    .line 139
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->scale:[Landroid/view/animation/Animation;

    const/4 v2, 0x1

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    sget v4, Lcom/android/keyguard/R$anim;->scale_2:I

    invoke-static {v3, v4}, Landroid/view/animation/AnimationUtils;->loadAnimation(Landroid/content/Context;I)Landroid/view/animation/Animation;

    move-result-object v3

    aput-object v3, v1, v2

    .line 140
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->scale:[Landroid/view/animation/Animation;

    const/4 v2, 0x2

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    sget v4, Lcom/android/keyguard/R$anim;->scale_3:I

    invoke-static {v3, v4}, Landroid/view/animation/AnimationUtils;->loadAnimation(Landroid/content/Context;I)Landroid/view/animation/Animation;

    move-result-object v3

    aput-object v3, v1, v2

    .line 141
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->scale:[Landroid/view/animation/Animation;

    const/4 v2, 0x3

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    sget v4, Lcom/android/keyguard/R$anim;->scale_4:I

    invoke-static {v3, v4}, Landroid/view/animation/AnimationUtils;->loadAnimation(Landroid/content/Context;I)Landroid/view/animation/Animation;

    move-result-object v3

    aput-object v3, v1, v2

    .line 143
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->scale:[Landroid/view/animation/Animation;

    const/4 v2, 0x4

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    sget v4, Lcom/android/keyguard/R$anim;->scale_5:I

    invoke-static {v3, v4}, Landroid/view/animation/AnimationUtils;->loadAnimation(Landroid/content/Context;I)Landroid/view/animation/Animation;

    move-result-object v3

    aput-object v3, v1, v2

    .line 144
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->scale:[Landroid/view/animation/Animation;

    const/4 v2, 0x5

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    sget v4, Lcom/android/keyguard/R$anim;->scale_6:I

    invoke-static {v3, v4}, Landroid/view/animation/AnimationUtils;->loadAnimation(Landroid/content/Context;I)Landroid/view/animation/Animation;

    move-result-object v3

    aput-object v3, v1, v2

    .line 148
    const/4 v0, 0x0

    .local v0, "i":I
    :goto_4f
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->scale:[Landroid/view/animation/Animation;

    array-length v1, v1

    if-ge v0, v1, :cond_60

    .line 149
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->scale:[Landroid/view/animation/Animation;

    aget-object v1, v1, v0

    const-wide/16 v2, 0x514

    invoke-virtual {v1, v2, v3}, Landroid/view/animation/Animation;->setDuration(J)V

    .line 148
    add-int/lit8 v0, v0, 0x1

    goto :goto_4f

    .line 151
    :cond_60
    sget v1, Lcom/android/keyguard/R$id;->keyguard_circle_effect_circle_main:I

    invoke-virtual {p0, v1}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->findViewById(I)Landroid/view/View;

    move-result-object v1

    check-cast v1, Landroid/widget/FrameLayout;

    iput-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    .line 154
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    invoke-virtual {v1}, Landroid/widget/FrameLayout;->removeAllViews()V

    .line 156
    return-void
.end method

.method private setSound()V
    .registers 9

    .prologue
    const/4 v7, 0x0

    const/4 v6, 0x1

    .line 573
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    if-nez v2, :cond_51

    .line 575
    const-string v2, "MassRippleUnlockTwin"

    const-string v3, "show mSoundPool is null"

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 576
    new-instance v2, Landroid/media/SoundPool;

    const/16 v3, 0xa

    invoke-direct {v2, v3, v6, v7}, Landroid/media/SoundPool;-><init>(III)V

    iput-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    .line 577
    const/4 v2, 0x2

    new-array v2, v2, [I

    iput-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    .line 579
    new-instance v0, Ljava/io/File;

    const-string v2, "/system/media/audio/ui/simple_ripple_down.ogg"

    invoke-direct {v0, v2}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 580
    .local v0, "checkDownFile":Ljava/io/File;
    if-eqz v0, :cond_52

    invoke-virtual {v0}, Ljava/io/File;->exists()Z

    move-result v2

    if-eqz v2, :cond_52

    .line 581
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    const-string v4, "/system/media/audio/ui/simple_ripple_down.ogg"

    invoke-virtual {v3, v4, v6}, Landroid/media/SoundPool;->load(Ljava/lang/String;I)I

    move-result v3

    aput v3, v2, v7

    .line 586
    :goto_36
    new-instance v1, Ljava/io/File;

    const-string v2, "/system/media/audio/ui/simple_ripple_up.ogg"

    invoke-direct {v1, v2}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 587
    .local v1, "checkUpFile":Ljava/io/File;
    if-eqz v1, :cond_61

    invoke-virtual {v1}, Ljava/io/File;->exists()Z

    move-result v2

    if-eqz v2, :cond_61

    .line 588
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    const-string v4, "/system/media/audio/ui/simple_ripple_up.ogg"

    invoke-virtual {v3, v4, v6}, Landroid/media/SoundPool;->load(Ljava/lang/String;I)I

    move-result v3

    aput v3, v2, v6

    .line 593
    .end local v0    # "checkDownFile":Ljava/io/File;
    .end local v1    # "checkUpFile":Ljava/io/File;
    :cond_51
    :goto_51
    return-void

    .line 583
    .restart local v0    # "checkDownFile":Ljava/io/File;
    :cond_52
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    iget-object v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    iget v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mRDownId:I

    invoke-virtual {v3, v4, v5, v6}, Landroid/media/SoundPool;->load(Landroid/content/Context;II)I

    move-result v3

    aput v3, v2, v7

    goto :goto_36

    .line 590
    .restart local v1    # "checkUpFile":Ljava/io/File;
    :cond_61
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    iget-object v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    iget v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mRUpId:I

    invoke-virtual {v3, v4, v5, v6}, Landroid/media/SoundPool;->load(Landroid/content/Context;II)I

    move-result v3

    aput v3, v2, v6

    goto :goto_51
.end method

.method private setValueOfDVFS(ZIZI)V
    .registers 8
    .param p1, "cpuClock"    # Z
    .param p2, "cpuClockIndex"    # I
    .param p3, "gpuFreq"    # Z
    .param p4, "gpuFreqIndex"    # I

    .prologue
    .line 821
    iput-boolean p1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictCPUClock:Z

    .line 822
    iput p2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->CPU_CLOCK_NUM:I

    .line 823
    const-string v0, "MassRippleUnlockTwin"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "== DVFS == isRestrictCPUClock = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget-boolean v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictCPUClock:Z

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 824
    const-string v0, "MassRippleUnlockTwin"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "== DVFS == CPU_CLOCK_NUM = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->CPU_CLOCK_NUM:I

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 827
    iput-boolean p3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictGPUFreq:Z

    .line 828
    iput p4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->GPU_FREQUNCY_NUM:I

    .line 829
    const-string v0, "MassRippleUnlockTwin"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "== DVFS == isRestrictGPUFreq = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget-boolean v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictGPUFreq:Z

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 830
    const-string v0, "MassRippleUnlockTwin"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "== DVFS == GPU_FREQUNCY_NUM = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->GPU_FREQUNCY_NUM:I

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 831
    return-void
.end method


# virtual methods
.method public cleanUp()V
    .registers 3

    .prologue
    const/4 v1, 0x0

    .line 466
    invoke-direct {p0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->clearAllViews()V

    .line 468
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    if-eqz v0, :cond_11

    .line 469
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    invoke-virtual {v0}, Landroid/media/SoundPool;->release()V

    .line 470
    iput-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    .line 471
    iput-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    .line 474
    :cond_11
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictCPUClock:Z

    if-nez v0, :cond_19

    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_23

    .line 476
    :cond_19
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    if-eqz v0, :cond_23

    .line 477
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;->sendEmptyMessage(I)Z

    .line 479
    :cond_23
    return-void
.end method

.method public getCircleSize(IZ)F
    .registers 6
    .param p1, "lineStorke"    # I
    .param p2, "isAffordance"    # Z

    .prologue
    const/high16 v2, 0x43910000    # 290.0f

    .line 337
    if-eqz p2, :cond_7

    .line 338
    const/high16 v0, 0x43600000    # 224.0f

    .line 340
    :goto_6
    return v0

    :cond_7
    iget v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mMovingRippleCount:I

    int-to-float v0, v0

    mul-float/2addr v0, v2

    const/high16 v1, 0x41a00000    # 20.0f

    mul-float/2addr v0, v1

    const/high16 v1, 0x42c80000    # 100.0f

    div-float/2addr v0, v1

    sub-float v0, v2, v0

    goto :goto_6
.end method

.method public getUnlockDelay()J
    .registers 3

    .prologue
    .line 506
    const-wide/16 v0, 0x385

    return-wide v0
.end method

.method public handleHoverEvent(Landroid/view/View;Landroid/view/MotionEvent;)Z
    .registers 4
    .param p1, "view"    # Landroid/view/View;
    .param p2, "event"    # Landroid/view/MotionEvent;

    .prologue
    .line 700
    const/4 v0, 0x0

    return v0
.end method

.method public handleTouchEvent(Landroid/view/View;Landroid/view/MotionEvent;)Z
    .registers 16
    .param p1, "view"    # Landroid/view/View;
    .param p2, "event"    # Landroid/view/MotionEvent;

    .prologue
    const/4 v12, 0x0

    const/4 v9, 0x2

    const/4 v8, -0x1

    const/4 v11, 0x1

    const/4 v10, 0x0

    .line 177
    invoke-virtual {p2}, Landroid/view/MotionEvent;->getRawX()F

    move-result v2

    .line 178
    .local v2, "insdieViewTouchedEventX":F
    invoke-virtual {p2}, Landroid/view/MotionEvent;->getRawY()F

    move-result v3

    .line 181
    .local v3, "insdieViewTouchedEventY":F
    iget-boolean v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->DEBUG:Z

    if-eqz v5, :cond_33

    .line 182
    const-string v5, "MassRippleUnlockTwin"

    new-instance v6, Ljava/lang/StringBuilder;

    invoke-direct {v6}, Ljava/lang/StringBuilder;-><init>()V

    const-string v7, "insdieViewTouchedEventX = "

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v6

    invoke-virtual {v6, v2}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object v6

    const-string v7, "insdieViewTouchedEventY = "

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v6

    invoke-virtual {v6, v3}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object v6

    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v6

    invoke-static {v5, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 184
    :cond_33
    invoke-virtual {p2}, Landroid/view/MotionEvent;->getActionMasked()I

    move-result v5

    if-nez v5, :cond_106

    .line 186
    const-string v5, "MassRippleUnlockTwin"

    const-string v6, "ACTION_DOWN"

    invoke-static {v5, v6}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    .line 188
    iget v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_X:I

    if-ne v5, v8, :cond_dc

    iget v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_Y:I

    if-ne v5, v8, :cond_dc

    .line 189
    const-string v5, "MassRippleUnlockTwin"

    const-string v6, "ACTION_DOWN First Touch"

    invoke-static {v5, v6}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    .line 190
    float-to-int v5, v2

    iput v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_X:I

    .line 191
    float-to-int v5, v3

    iput v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_Y:I

    .line 200
    :goto_55
    iput v10, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mMovingRippleCount:I

    .line 201
    iget-object v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    if-nez v5, :cond_9e

    .line 203
    new-instance v5, Landroid/media/SoundPool;

    const/16 v6, 0xa

    invoke-direct {v5, v6, v11, v10}, Landroid/media/SoundPool;-><init>(III)V

    iput-object v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    .line 204
    new-array v5, v9, [I

    iput-object v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    .line 206
    new-instance v0, Ljava/io/File;

    const-string v5, "/system/media/audio/ui/simple_ripple_down.ogg"

    invoke-direct {v0, v5}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 207
    .local v0, "checkDownFile":Ljava/io/File;
    if-eqz v0, :cond_e8

    invoke-virtual {v0}, Ljava/io/File;->exists()Z

    move-result v5

    if-eqz v5, :cond_e8

    .line 208
    iget-object v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    iget-object v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    const-string v7, "/system/media/audio/ui/simple_ripple_down.ogg"

    invoke-virtual {v6, v7, v11}, Landroid/media/SoundPool;->load(Ljava/lang/String;I)I

    move-result v6

    aput v6, v5, v10

    .line 213
    :goto_83
    new-instance v1, Ljava/io/File;

    const-string v5, "/system/media/audio/ui/simple_ripple_up.ogg"

    invoke-direct {v1, v5}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 214
    .local v1, "checkUpFile":Ljava/io/File;
    if-eqz v1, :cond_f7

    invoke-virtual {v1}, Ljava/io/File;->exists()Z

    move-result v5

    if-eqz v5, :cond_f7

    .line 215
    iget-object v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    iget-object v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    const-string v7, "/system/media/audio/ui/simple_ripple_up.ogg"

    invoke-virtual {v6, v7, v11}, Landroid/media/SoundPool;->load(Ljava/lang/String;I)I

    move-result v6

    aput v6, v5, v11

    .line 220
    .end local v0    # "checkDownFile":Ljava/io/File;
    .end local v1    # "checkUpFile":Ljava/io/File;
    :cond_9e
    :goto_9e
    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J

    move-result-wide v6

    iput-wide v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->prevPressTime:J

    .line 221
    const-wide/16 v6, 0x0

    iput-wide v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->diffPressTime:J

    .line 223
    iget v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventX:F

    iget v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventY:F

    invoke-virtual {p0, v5, v6, v10, v10}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->rippeDown(FFIZ)V

    .line 224
    invoke-direct {p0, v10}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->playSound(I)V

    .line 227
    new-instance v4, Landroid/os/Message;

    invoke-direct {v4}, Landroid/os/Message;-><init>()V

    .line 228
    .local v4, "message":Landroid/os/Message;
    iput v10, v4, Landroid/os/Message;->what:I

    .line 229
    iget v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventX:F

    float-to-int v5, v5

    iput v5, v4, Landroid/os/Message;->arg1:I

    .line 230
    iget v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventY:F

    float-to-int v5, v5

    iput v5, v4, Landroid/os/Message;->arg2:I

    .line 231
    iget-object v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mHandler:Landroid/os/Handler;

    const-wide/16 v6, 0x190

    invoke-virtual {v5, v4, v6, v7}, Landroid/os/Handler;->sendMessageDelayed(Landroid/os/Message;J)Z

    .line 233
    iget-boolean v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictCPUClock:Z

    if-nez v5, :cond_d2

    iget-boolean v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictGPUFreq:Z

    if-eqz v5, :cond_db

    .line 235
    :cond_d2
    iget-object v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    if-eqz v5, :cond_db

    .line 236
    iget-object v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    invoke-virtual {v5, v10}, Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;->sendEmptyMessage(I)Z

    .line 290
    .end local v4    # "message":Landroid/os/Message;
    :cond_db
    :goto_db
    return v11

    .line 194
    :cond_dc
    iput v8, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_X:I

    .line 195
    iput v8, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_Y:I

    .line 196
    iput v12, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->prevMovingDistance:F

    .line 197
    const-wide/16 v6, 0x0

    iput-wide v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDistanceRatio:D

    goto/16 :goto_55

    .line 210
    .restart local v0    # "checkDownFile":Ljava/io/File;
    :cond_e8
    iget-object v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    iget-object v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    iget-object v7, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    iget v8, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mRDownId:I

    invoke-virtual {v6, v7, v8, v11}, Landroid/media/SoundPool;->load(Landroid/content/Context;II)I

    move-result v6

    aput v6, v5, v10

    goto :goto_83

    .line 217
    .restart local v1    # "checkUpFile":Ljava/io/File;
    :cond_f7
    iget-object v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->sounds:[I

    iget-object v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mSoundPool:Landroid/media/SoundPool;

    iget-object v7, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    iget v8, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mRUpId:I

    invoke-virtual {v6, v7, v8, v11}, Landroid/media/SoundPool;->load(Landroid/content/Context;II)I

    move-result v6

    aput v6, v5, v11

    goto :goto_9e

    .line 239
    .end local v0    # "checkDownFile":Ljava/io/File;
    .end local v1    # "checkUpFile":Ljava/io/File;
    :cond_106
    invoke-virtual {p2}, Landroid/view/MotionEvent;->getActionMasked()I

    move-result v5

    if-ne v5, v9, :cond_167

    invoke-virtual {p2}, Landroid/view/MotionEvent;->getActionIndex()I

    move-result v5

    if-nez v5, :cond_167

    .line 242
    iget-boolean v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->DEBUG:Z

    if-eqz v5, :cond_11d

    .line 243
    const-string v5, "MassRippleUnlockTwin"

    const-string v6, "ACTION_MOVE"

    invoke-static {v5, v6}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    .line 245
    :cond_11d
    invoke-virtual {p0, v2, v3}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->moveToDistanceIs20percent(FF)Z

    move-result v5

    if-eqz v5, :cond_13b

    .line 247
    iget v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->drawRippleCount:I

    rem-int/lit8 v5, v5, 0x2

    if-nez v5, :cond_162

    .line 248
    invoke-virtual {p0, v2, v3, v9, v10}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->rippeDown(FFIZ)V

    .line 252
    :goto_12c
    iget v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->drawRippleCount:I

    add-int/lit8 v5, v5, 0x1

    iput v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->drawRippleCount:I

    .line 253
    iget v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mMovingRippleCount:I

    add-int/lit8 v5, v5, 0x1

    iput v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mMovingRippleCount:I

    .line 254
    invoke-direct {p0, v11}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->playDragSound(I)V

    .line 258
    :cond_13b
    iget-wide v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDistanceRatio:D

    const-wide v8, 0x3ff4ccccc0000000L    # 1.2999999523162842

    cmpl-double v5, v6, v8

    if-lez v5, :cond_db

    .line 259
    const-string v5, "MassRippleUnlockTwin"

    new-instance v6, Ljava/lang/StringBuilder;

    invoke-direct {v6}, Ljava/lang/StringBuilder;-><init>()V

    const-string v7, "mDistanceRatio ove DRAG threshold "

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v6

    iget-wide v8, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDistanceRatio:D

    invoke-virtual {v6, v8, v9}, Ljava/lang/StringBuilder;->append(D)Ljava/lang/StringBuilder;

    move-result-object v6

    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v6

    invoke-static {v5, v6}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    goto/16 :goto_db

    .line 250
    :cond_162
    const/4 v5, 0x3

    invoke-virtual {p0, v2, v3, v5, v10}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->rippeDown(FFIZ)V

    goto :goto_12c

    .line 261
    :cond_167
    invoke-virtual {p2}, Landroid/view/MotionEvent;->getActionMasked()I

    move-result v5

    if-eq v5, v11, :cond_174

    invoke-virtual {p2}, Landroid/view/MotionEvent;->getActionMasked()I

    move-result v5

    const/4 v6, 0x3

    if-ne v5, v6, :cond_1bc

    .line 264
    :cond_174
    const-string v5, "MassRippleUnlockTwin"

    const-string v6, "ACTION_UP"

    invoke-static {v5, v6}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    .line 266
    iput v8, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_X:I

    .line 267
    iput v8, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_Y:I

    .line 268
    iput v12, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->prevMovingDistance:F

    .line 270
    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J

    move-result-wide v6

    iget-wide v8, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->prevPressTime:J

    sub-long/2addr v6, v8

    iput-wide v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->diffPressTime:J

    .line 272
    iget-wide v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->diffPressTime:J

    const-wide/16 v8, 0x258

    cmp-long v5, v6, v8

    if-lez v5, :cond_195

    .line 273
    invoke-direct {p0, v10}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->playSound(I)V

    .line 276
    :cond_195
    iget-wide v6, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDistanceRatio:D

    const-wide v8, 0x3fe99999a0000000L    # 0.800000011920929

    cmpl-double v5, v6, v8

    if-lez v5, :cond_db

    .line 277
    const-string v5, "MassRippleUnlockTwin"

    new-instance v6, Ljava/lang/StringBuilder;

    invoke-direct {v6}, Ljava/lang/StringBuilder;-><init>()V

    const-string v7, "mDistanceRatio ove RELEASE threshold "

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v6

    iget-wide v8, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDistanceRatio:D

    invoke-virtual {v6, v8, v9}, Ljava/lang/StringBuilder;->append(D)Ljava/lang/StringBuilder;

    move-result-object v6

    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v6

    invoke-static {v5, v6}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    goto/16 :goto_db

    .line 279
    :cond_1bc
    invoke-virtual {p2}, Landroid/view/MotionEvent;->getActionMasked()I

    move-result v5

    const/16 v6, 0x9

    if-ne v5, v6, :cond_db

    .line 281
    const-string v5, "MassRippleUnlockTwin"

    const-string v6, "ACTION_HOVER_ENTER"

    invoke-static {v5, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 283
    iget-boolean v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictCPUClock:Z

    if-nez v5, :cond_1d3

    iget-boolean v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictGPUFreq:Z

    if-eqz v5, :cond_db

    .line 285
    :cond_1d3
    iget-object v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    if-eqz v5, :cond_db

    .line 286
    iget-object v5, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    invoke-virtual {v5, v10}, Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;->sendEmptyMessage(I)Z

    goto/16 :goto_db
.end method

.method public handleTouchEventForPatternLock(Landroid/view/View;Landroid/view/MotionEvent;)Z
    .registers 8
    .param p1, "view"    # Landroid/view/View;
    .param p2, "event"    # Landroid/view/MotionEvent;

    .prologue
    const/4 v4, 0x0

    .line 676
    invoke-virtual {p2}, Landroid/view/MotionEvent;->getActionMasked()I

    move-result v0

    .line 677
    .local v0, "action":I
    const-string v1, "MassRippleUnlockTwin"

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "handleTouchEventForPatternLock action = "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 679
    packed-switch v0, :pswitch_data_3a

    .line 690
    :cond_20
    :goto_20
    return v4

    .line 681
    :pswitch_21
    const-string v1, "MassRippleUnlockTwin"

    const-string v2, "ACTION_DOWN => ACTION_HOVER_ENTER"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 682
    iget-boolean v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictCPUClock:Z

    if-nez v1, :cond_30

    iget-boolean v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictGPUFreq:Z

    if-eqz v1, :cond_20

    .line 684
    :cond_30
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    if-eqz v1, :cond_20

    .line 685
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    invoke-virtual {v1, v4}, Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;->sendEmptyMessage(I)Z

    goto :goto_20

    .line 679
    :pswitch_data_3a
    .packed-switch 0x0
        :pswitch_21
    .end packed-switch
.end method

.method public handleUnlock(Landroid/view/View;Landroid/view/MotionEvent;)V
    .registers 6
    .param p1, "view"    # Landroid/view/View;
    .param p2, "event"    # Landroid/view/MotionEvent;

    .prologue
    const/4 v2, -0x1

    .line 423
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictCPUClock:Z

    if-nez v0, :cond_9

    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_13

    .line 425
    :cond_9
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    if-eqz v0, :cond_13

    .line 426
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;->sendEmptyMessage(I)Z

    .line 428
    :cond_13
    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_X:I

    .line 429
    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_Y:I

    .line 430
    const/4 v0, 0x0

    iput v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->prevMovingDistance:F

    .line 431
    const/4 v0, 0x1

    iput-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isStartUnlock:Z

    .line 432
    return-void
.end method

.method public moveToDistanceIs20percent(FF)Z
    .registers 21
    .param p1, "x"    # F
    .param p2, "y"    # F

    .prologue
    .line 358
    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_X:I

    const/4 v12, -0x1

    if-ne v9, v12, :cond_10

    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_Y:I

    const/4 v12, -0x1

    if-ne v9, v12, :cond_10

    .line 359
    const/4 v9, 0x0

    .line 388
    :goto_f
    return v9

    .line 362
    :cond_10
    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_X:I

    move/from16 v0, p1

    float-to-int v12, v0

    sub-int/2addr v9, v12

    invoke-static {v9}, Ljava/lang/Math;->abs(I)I

    move-result v2

    .line 363
    .local v2, "diffX":I
    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->firstTouch_Y:I

    move/from16 v0, p2

    float-to-int v12, v0

    sub-int/2addr v9, v12

    invoke-static {v9}, Ljava/lang/Math;->abs(I)I

    move-result v3

    .line 365
    .local v3, "diffY":I
    move-object/from16 v0, p0

    iget-boolean v9, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->DEBUG:Z

    if-eqz v9, :cond_50

    .line 366
    const-string v9, "MassRippleUnlockTwin"

    new-instance v12, Ljava/lang/StringBuilder;

    invoke-direct {v12}, Ljava/lang/StringBuilder;-><init>()V

    const-string v13, "onTouchEvent() : diffX="

    invoke-virtual {v12, v13}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v12

    invoke-virtual {v12, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v12

    const-string v13, ",diffY="

    invoke-virtual {v12, v13}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v12

    invoke-virtual {v12, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v12

    invoke-virtual {v12}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v12

    invoke-static {v9, v12}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 368
    :cond_50
    int-to-double v12, v2

    const-wide/high16 v14, 0x4000000000000000L    # 2.0

    invoke-static {v12, v13, v14, v15}, Ljava/lang/Math;->pow(DD)D

    move-result-wide v12

    int-to-double v14, v3

    const-wide/high16 v16, 0x4000000000000000L    # 2.0

    invoke-static/range {v14 .. v17}, Ljava/lang/Math;->pow(DD)D

    move-result-wide v14

    add-double v6, v12, v14

    .line 369
    .local v6, "distance_square":D
    invoke-static {v6, v7}, Ljava/lang/Math;->sqrt(D)D

    move-result-wide v4

    .line 370
    .local v4, "distance":D
    move-object/from16 v0, p0

    iget-object v9, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    invoke-virtual {v9}, Landroid/widget/FrameLayout;->getWidth()I

    move-result v9

    move-object/from16 v0, p0

    iget-object v12, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    invoke-virtual {v12}, Landroid/widget/FrameLayout;->getHeight()I

    move-result v12

    if-ge v9, v12, :cond_bf

    move-object/from16 v0, p0

    iget-object v9, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    invoke-virtual {v9}, Landroid/widget/FrameLayout;->getWidth()I

    move-result v8

    .line 372
    .local v8, "min":I
    :goto_7e
    int-to-double v12, v8

    const-wide/high16 v14, 0x4000000000000000L    # 2.0

    div-double v10, v12, v14

    .line 373
    .local v10, "threshold":D
    div-double v12, v4, v10

    move-object/from16 v0, p0

    iput-wide v12, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDistanceRatio:D

    .line 376
    const-string v9, "MassRippleUnlockTwin"

    new-instance v12, Ljava/lang/StringBuilder;

    invoke-direct {v12}, Ljava/lang/StringBuilder;-><init>()V

    const-string v13, "onTouchEvent() : threshold="

    invoke-virtual {v12, v13}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v12

    invoke-virtual {v12, v10, v11}, Ljava/lang/StringBuilder;->append(D)Ljava/lang/StringBuilder;

    move-result-object v12

    const-string v13, ",mDistanceRatio="

    invoke-virtual {v12, v13}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v12

    move-object/from16 v0, p0

    iget-wide v14, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDistanceRatio:D

    invoke-virtual {v12, v14, v15}, Ljava/lang/StringBuilder;->append(D)Ljava/lang/StringBuilder;

    move-result-object v12

    invoke-virtual {v12}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v12

    invoke-static {v9, v12}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 378
    move-object/from16 v0, p0

    iget-wide v12, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDistanceRatio:D

    const-wide v14, 0x3fdccccccccccccdL    # 0.45

    cmpg-double v9, v12, v14

    if-gez v9, :cond_c8

    .line 379
    const/4 v9, 0x0

    goto/16 :goto_f

    .line 370
    .end local v8    # "min":I
    .end local v10    # "threshold":D
    :cond_bf
    move-object/from16 v0, p0

    iget-object v9, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    invoke-virtual {v9}, Landroid/widget/FrameLayout;->getHeight()I

    move-result v8

    goto :goto_7e

    .line 381
    .restart local v8    # "min":I
    .restart local v10    # "threshold":D
    :cond_c8
    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->prevMovingDistance:F

    float-to-double v12, v9

    move-object/from16 v0, p0

    iget-wide v14, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDistanceRatio:D

    sub-double/2addr v12, v14

    invoke-static {v12, v13}, Ljava/lang/Math;->abs(D)D

    move-result-wide v12

    const-wide v14, 0x3fdccccccccccccdL    # 0.45

    cmpl-double v9, v12, v14

    if-lez v9, :cond_eb

    .line 383
    move-object/from16 v0, p0

    iget-wide v12, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDistanceRatio:D

    double-to-float v9, v12

    move-object/from16 v0, p0

    iput v9, v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->prevMovingDistance:F

    .line 384
    const/4 v9, 0x1

    goto/16 :goto_f

    .line 388
    :cond_eb
    const/4 v9, 0x0

    goto/16 :goto_f
.end method

.method public onInterceptTouchEvent(Landroid/view/MotionEvent;)Z
    .registers 7
    .param p1, "event"    # Landroid/view/MotionEvent;

    .prologue
    .line 394
    invoke-virtual {p1}, Landroid/view/MotionEvent;->getX()F

    move-result v0

    .line 395
    .local v0, "originalCircleX":F
    invoke-virtual {p1}, Landroid/view/MotionEvent;->getY()F

    move-result v1

    .line 397
    .local v1, "originalCircleY":F
    iget-boolean v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->DEBUG:Z

    if-eqz v2, :cond_54

    .line 398
    const-string v2, "MassRippleUnlockTwin"

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, " originalCircleX = "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v4, ", originalCircleY"

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    .line 399
    const-string v2, "MassRippleUnlockTwin"

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, " touchedEventX = "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    iget v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventX:F

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v4, ", touchedEventY"

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    iget v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventY:F

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    .line 401
    :cond_54
    iput v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventX:F

    .line 402
    iput v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventY:F

    .line 404
    const/4 v2, 0x0

    return v2
.end method

.method public playLockSound()V
    .registers 1

    .prologue
    .line 532
    return-void
.end method

.method public releaseBooster(I)V
    .registers 5
    .param p1, "type"    # I

    .prologue
    const/4 v2, 0x0

    .line 770
    if-nez p1, :cond_1a

    .line 772
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedCPUClockTable:[I

    if-eqz v0, :cond_19

    .line 774
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    if-eqz v0, :cond_17

    .line 776
    const-string v0, "MassRippleUnlockTwin"

    const-string v1, "== DVFS == cpu MaxClock Booster.release()!!!"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 777
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    invoke-virtual {v0}, Landroid/os/DVFSHelper;->release()V

    .line 780
    :cond_17
    iput-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedCPUClockTable:[I

    .line 796
    :cond_19
    :goto_19
    return-void

    .line 785
    :cond_1a
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedGPUFreqTable:[I

    if-eqz v0, :cond_19

    .line 787
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    if-eqz v0, :cond_2e

    .line 789
    const-string v0, "MassRippleUnlockTwin"

    const-string v1, "== DVFS == gpu MaxFreq Booster.release()!!!"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 790
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    invoke-virtual {v0}, Landroid/os/DVFSHelper;->release()V

    .line 793
    :cond_2e
    iput-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->supportedGPUFreqTable:[I

    goto :goto_19
.end method

.method public reset()V
    .registers 3

    .prologue
    .line 484
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictCPUClock:Z

    if-nez v0, :cond_8

    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_12

    .line 486
    :cond_8
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    if-eqz v0, :cond_12

    .line 487
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;->sendEmptyMessage(I)Z

    .line 489
    :cond_12
    const-string v0, "MassRippleUnlockTwin"

    const-string v1, "reset"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 492
    return-void
.end method

.method public rippeDown(FFIZ)V
    .registers 20
    .param p1, "x"    # F
    .param p2, "y"    # F
    .param p3, "lineStroke"    # I
    .param p4, "isAffordance"    # Z

    .prologue
    .line 298
    iget v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mMovingRippleCount:I

    const/4 v3, 0x2

    if-le v2, v3, :cond_6

    .line 333
    :goto_5
    return-void

    .line 301
    :cond_6
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    aget-object v2, v2, v3

    if-eqz v2, :cond_34

    .line 302
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    aget-object v2, v2, v3

    invoke-virtual {v2}, Landroid/widget/ImageView;->clearAnimation()V

    .line 303
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    aget-object v2, v2, v3

    const/16 v3, 0x8

    invoke-virtual {v2, v3}, Landroid/widget/ImageView;->setVisibility(I)V

    .line 304
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    aget-object v3, v3, v4

    invoke-virtual {v2, v3}, Landroid/widget/FrameLayout;->removeView(Landroid/view/View;)V

    .line 305
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    const/4 v4, 0x0

    aput-object v4, v2, v3

    .line 308
    :cond_34
    move/from16 v0, p3

    move/from16 v1, p4

    invoke-virtual {p0, v0, v1}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->getCircleSize(IZ)F

    move-result v8

    .line 310
    .local v8, "CIRCLE_MAX_SIZE":F
    iget-object v13, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v14, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    new-instance v2, Lcom/android/keyguard/sec/MassRippleImageView;

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    iget-object v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->typeStorke:[F

    aget v4, v4, p3

    float-to-int v5, v8

    float-to-int v6, v8

    const v7, 0x44a28000    # 1300.0f

    invoke-direct/range {v2 .. v7}, Lcom/android/keyguard/sec/MassRippleImageView;-><init>(Landroid/content/Context;FIIF)V

    aput-object v2, v13, v14

    .line 312
    new-instance v9, Landroid/widget/FrameLayout$LayoutParams;

    const/4 v2, -0x2

    const/4 v3, -0x2

    invoke-direct {v9, v2, v3}, Landroid/widget/FrameLayout$LayoutParams;-><init>(II)V

    .line 313
    .local v9, "addViewParams":Landroid/widget/FrameLayout$LayoutParams;
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    aget-object v3, v3, v4

    invoke-virtual {v2, v3, v9}, Landroid/widget/FrameLayout;->addView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V

    .line 315
    move/from16 v0, p1

    float-to-int v2, v0

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    aget-object v3, v3, v4

    invoke-virtual {v3}, Landroid/widget/ImageView;->getBackground()Landroid/graphics/drawable/Drawable;

    move-result-object v3

    invoke-virtual {v3}, Landroid/graphics/drawable/Drawable;->getIntrinsicWidth()I

    move-result v3

    div-int/lit8 v3, v3, 0x2

    sub-int v10, v2, v3

    .line 316
    .local v10, "moveX":I
    move/from16 v0, p2

    float-to-int v2, v0

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    aget-object v3, v3, v4

    invoke-virtual {v3}, Landroid/widget/ImageView;->getBackground()Landroid/graphics/drawable/Drawable;

    move-result-object v3

    invoke-virtual {v3}, Landroid/graphics/drawable/Drawable;->getIntrinsicHeight()I

    move-result v3

    div-int/lit8 v3, v3, 0x2

    sub-int v11, v2, v3

    .line 317
    .local v11, "moveY":I
    const-string v2, "MassRippleUnlockTwin"

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "moveX X = "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3, v10}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v4, "moveY = "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3, v11}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 319
    new-instance v12, Landroid/widget/FrameLayout$LayoutParams;

    const/4 v2, -0x2

    const/4 v3, -0x2

    invoke-direct {v12, v2, v3}, Landroid/widget/FrameLayout$LayoutParams;-><init>(II)V

    .line 321
    .local v12, "params":Landroid/widget/FrameLayout$LayoutParams;
    iput v10, v12, Landroid/widget/FrameLayout$LayoutParams;->leftMargin:I

    .line 322
    iput v11, v12, Landroid/widget/FrameLayout$LayoutParams;->topMargin:I

    .line 323
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    aget-object v2, v2, v3

    invoke-virtual {v2}, Landroid/widget/ImageView;->getBackground()Landroid/graphics/drawable/Drawable;

    move-result-object v2

    invoke-virtual {v2}, Landroid/graphics/drawable/Drawable;->getIntrinsicWidth()I

    move-result v2

    iput v2, v12, Landroid/widget/FrameLayout$LayoutParams;->width:I

    .line 324
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    aget-object v2, v2, v3

    invoke-virtual {v2}, Landroid/widget/ImageView;->getBackground()Landroid/graphics/drawable/Drawable;

    move-result-object v2

    invoke-virtual {v2}, Landroid/graphics/drawable/Drawable;->getIntrinsicHeight()I

    move-result v2

    iput v2, v12, Landroid/widget/FrameLayout$LayoutParams;->height:I

    .line 326
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    aget-object v2, v2, v3

    invoke-virtual {v2, v12}, Landroid/widget/ImageView;->setLayoutParams(Landroid/view/ViewGroup$LayoutParams;)V

    .line 328
    iget-object v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->massRipple:[Landroid/widget/ImageView;

    iget v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    aget-object v2, v2, v3

    iget-object v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->scale:[Landroid/view/animation/Animation;

    iget v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    aget-object v3, v3, v4

    invoke-virtual {v2, v3}, Landroid/widget/ImageView;->startAnimation(Landroid/view/animation/Animation;)V

    .line 330
    iget v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    add-int/lit8 v2, v2, 0x1

    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    rem-int/lit8 v2, v2, 0x6

    iput v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    .line 331
    const-string v2, "MassRippleUnlockTwin"

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "lineStroke = "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    move/from16 v0, p3

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    .line 332
    const-string v2, "MassRippleUnlockTwin"

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "indexAni = "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    iget v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->indexAni:I

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    goto/16 :goto_5
.end method

.method public screenTurnedOff()V
    .registers 1

    .prologue
    .line 670
    invoke-direct {p0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->clearAllViews()V

    .line 671
    return-void
.end method

.method public screenTurnedOn()V
    .registers 4

    .prologue
    const/4 v2, 0x0

    .line 497
    const-string v0, "MassRippleUnlockTwin"

    const-string v1, "screenTurnedOn"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 498
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    invoke-virtual {v0, v2}, Landroid/widget/FrameLayout;->setVisibility(I)V

    .line 499
    iput-boolean v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isStartUnlock:Z

    .line 501
    return-void
.end method

.method public setHidden(Z)V
    .registers 2
    .param p1, "isHidden"    # Z

    .prologue
    .line 696
    return-void
.end method

.method public show()V
    .registers 4

    .prologue
    .line 437
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroid/widget/FrameLayout;->setVisibility(I)V

    .line 439
    invoke-direct {p0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->checkSound()V

    .line 440
    invoke-direct {p0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->setSound()V

    .line 442
    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictCPUClock:Z

    if-nez v0, :cond_14

    iget-boolean v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_52

    .line 444
    :cond_14
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    if-nez v0, :cond_52

    .line 446
    const-string v0, "MassRippleUnlockTwin"

    const-string v1, "== DVFS == new DVFSHandlerForMassRipple"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 447
    new-instance v0, Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    invoke-direct {v0, p0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;-><init>(Lcom/android/keyguard/sec/MassRippleUnlockTwin;)V

    iput-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mDVFSHandlerMassRipple:Lcom/android/keyguard/sec/MassRippleUnlockTwin$DVFSHandlerForMassRipple;

    .line 449
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    if-nez v0, :cond_3c

    .line 451
    const-string v0, "MassRippleUnlockTwin"

    const-string v1, "== DVFS == new cpuMaxClockBooster"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 452
    new-instance v0, Landroid/os/DVFSHelper;

    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    const/16 v2, 0xd

    invoke-direct {v0, v1, v2}, Landroid/os/DVFSHelper;-><init>(Landroid/content/Context;I)V

    iput-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    .line 454
    :cond_3c
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    if-nez v0, :cond_52

    .line 456
    const-string v0, "MassRippleUnlockTwin"

    const-string v1, "== DVFS == new gpuMaxFreqBooster"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 457
    new-instance v0, Landroid/os/DVFSHelper;

    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mContext:Landroid/content/Context;

    const/16 v2, 0x11

    invoke-direct {v0, v1, v2}, Landroid/os/DVFSHelper;-><init>(Landroid/content/Context;I)V

    iput-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    .line 461
    :cond_52
    return-void
.end method

.method public showUnlockAffordance(JLandroid/graphics/Rect;)V
    .registers 11
    .param p1, "startDelay"    # J
    .param p3, "rect"    # Landroid/graphics/Rect;

    .prologue
    const/4 v5, 0x1

    const/4 v4, 0x0

    .line 512
    const-string v1, "MassRippleUnlockTwin"

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "showUnlockAffordance : "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    iget v3, p3, Landroid/graphics/Rect;->left:I

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    const-string v3, ", "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    iget v3, p3, Landroid/graphics/Rect;->right:I

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    const-string v3, ", "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    iget v3, p3, Landroid/graphics/Rect;->top:I

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    const-string v3, ", "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    iget v3, p3, Landroid/graphics/Rect;->bottom:I

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    const-string v3, ", startDelay : "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2, p1, p2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 513
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mCircleMain:Landroid/widget/FrameLayout;

    invoke-virtual {v1, v4}, Landroid/widget/FrameLayout;->setVisibility(I)V

    .line 514
    iput v4, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mMovingRippleCount:I

    .line 516
    iget v1, p3, Landroid/graphics/Rect;->left:I

    iget v2, p3, Landroid/graphics/Rect;->right:I

    iget v3, p3, Landroid/graphics/Rect;->left:I

    sub-int/2addr v2, v3

    div-int/lit8 v2, v2, 0x2

    add-int/2addr v1, v2

    int-to-float v1, v1

    iput v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventX:F

    .line 517
    iget v1, p3, Landroid/graphics/Rect;->top:I

    iget v2, p3, Landroid/graphics/Rect;->bottom:I

    iget v3, p3, Landroid/graphics/Rect;->top:I

    sub-int/2addr v2, v3

    div-int/lit8 v2, v2, 0x2

    add-int/2addr v1, v2

    int-to-float v1, v1

    iput v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventY:F

    .line 519
    iget v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventX:F

    iget v2, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventY:F

    invoke-virtual {p0, v1, v2, v4, v5}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->rippeDown(FFIZ)V

    .line 520
    new-instance v0, Landroid/os/Message;

    invoke-direct {v0}, Landroid/os/Message;-><init>()V

    .line 521
    .local v0, "message":Landroid/os/Message;
    iput v5, v0, Landroid/os/Message;->what:I

    .line 522
    iget v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventX:F

    float-to-int v1, v1

    iput v1, v0, Landroid/os/Message;->arg1:I

    .line 523
    iget v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->touchedEventY:F

    float-to-int v1, v1

    iput v1, v0, Landroid/os/Message;->arg2:I

    .line 524
    iget-object v1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->mHandler:Landroid/os/Handler;

    const-wide/16 v2, 0x190

    invoke-virtual {v1, v0, v2, v3}, Landroid/os/Handler;->sendMessageDelayed(Landroid/os/Message;J)Z

    .line 526
    return-void
.end method

.method public translatedFromDPToPixel(F)F
    .registers 8
    .param p1, "dp"    # F

    .prologue
    .line 344
    const/4 v2, 0x0

    .line 345
    .local v2, "ret":F
    new-instance v1, Landroid/util/DisplayMetrics;

    invoke-direct {v1}, Landroid/util/DisplayMetrics;-><init>()V

    .line 346
    .local v1, "metrics":Landroid/util/DisplayMetrics;
    invoke-virtual {p0}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->getResources()Landroid/content/res/Resources;

    move-result-object v3

    invoke-virtual {v3}, Landroid/content/res/Resources;->getDisplayMetrics()Landroid/util/DisplayMetrics;

    move-result-object v1

    .line 347
    iget v3, v1, Landroid/util/DisplayMetrics;->densityDpi:I

    int-to-float v0, v3

    .line 348
    .local v0, "dpi":F
    const/high16 v3, 0x43200000    # 160.0f

    div-float v3, v0, v3

    mul-float v2, p1, v3

    .line 349
    iget-boolean v3, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->DEBUG:Z

    if-eqz v3, :cond_3d

    .line 350
    const-string v3, "MassRippleUnlockTwin"

    new-instance v4, Ljava/lang/StringBuilder;

    invoke-direct {v4}, Ljava/lang/StringBuilder;-><init>()V

    const-string v5, "dp = "

    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v4

    invoke-virtual {v4, p1}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object v4

    const-string v5, ", to Pixel = "

    invoke-virtual {v4, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v4

    invoke-virtual {v4, v2}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object v4

    invoke-virtual {v4}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v4

    invoke-static {v3, v4}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    .line 351
    :cond_3d
    return v2
.end method

.method public update()V
    .registers 1

    .prologue
    .line 665
    return-void
.end method
