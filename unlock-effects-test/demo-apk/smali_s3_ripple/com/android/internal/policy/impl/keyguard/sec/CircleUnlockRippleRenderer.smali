.class public Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;
.super Ljava/lang/Object;
.source "CircleUnlockRippleRenderer.java"

# interfaces
.implements Landroid/opengl/GLSurfaceView$Renderer;
.implements Landroid/hardware/scontext/SContextListener;


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;,
        Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$SoundPoolThread;
    }
.end annotation


# static fields
.field private static final DEFAULT_WALLPAPER_IMAGE_PATH:Ljava/lang/String; = "/system/wallpaper/lockscreen_default_wallpaper.jpg"

.field private static final DEFAULT_WALLPAPER_IMAGE_PATH_MULTI_CSC:Ljava/lang/String; = "/system/csc_contents/lockscreen_default_wallpaper.jpg"

.field private static final DEFAULT_WALLPAPER_IMAGE_PATH_MULTI_CSC_PNG:Ljava/lang/String; = "/system/csc_contents/lockscreen_default_wallpaper.png"

.field private static final DEFAULT_WALLPAPER_IMAGE_PATH_PNG:Ljava/lang/String; = "/system/wallpaper/lockscreen_default_wallpaper.png"

.field private static final INK_DISABLE:I = 0x0

.field private static final LANDSCAPE_WALLPAPER_IMAGE_PATH:Ljava/lang/String; = "/data/data/com.sec.android.gallery3d/lockscreen_wallpaper_land.jpg"

.field private static final PORTRAIT_WALLPAPER_IMAGE_PATH:Ljava/lang/String; = "/data/data/com.sec.android.gallery3d/lockscreen_wallpaper_ripple.jpg"

.field private static mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;


# instance fields
.field private final ACQUIRE_DVFS:I

.field BGResId:I

.field private CPU_CLOCK_NUM:I

.field private final CPU_CLOK_CONTROL:I

.field private final CREATEDED_CPU:I

.field private final CREATEDED_GPU:I

.field private CurrentState:I

.field private final DBG:Z

.field private final DESTROYED_CPU:I

.field private final DESTROYED_GPU:I

.field private GPU_FREQUNCY_NUM:I

.field private final GPU_FREQ_CONTROL:I

.field private final GRAVITY_EFFECT_LEFT:I

.field private final GRAVITY_EFFECT_RIGHT:I

.field private HDMI_VIEW_HEIGHT_FOR_P4_NOTE_10_1:I

.field private HDMI_WIDOW_HEIGHT_FOR_P4_NOTE_10_1:I

.field private HOVER_EXPONENT_RATIO_MAX:F

.field private HOVER_EXPONENT_RATIO_MIN:F

.field private HOVER_FRESENL_MAX:F

.field private HOVER_FRESENL_MIN:F

.field private HOVER_INTENSITY_MAX:F

.field private HOVER_SPECULAR_RATIO_MAX:F

.field private HOVER_SPECULAR_RATIO_MIN:F

.field private MESH_SIZE_HEIGHT:I

.field private MESH_SIZE_WIDTH:I

.field MarkcuasticsTMix:F

.field private NUM_DETAILS_HEIGHT:I

.field private NUM_DETAILS_WIDTH:I

.field private final REDUCTION_RATE_NORMAL:F

.field private final REDUCTION_RATE_RAIN:F

.field private final REDUCTION_RATE_WAVE:F

.field private final REDUCTION_RATE_WAVE2:F

.field private final RELEASE_DVFS:I

.field private final RIPPLE_WAIT_TIME:J

.field ReferencePoint:F

.field final SOUND_ID_DOWN:I

.field final SOUND_ID_GRAVITY:I

.field final SOUND_ID_UP:I

.field private SURFACE_DETAILS_HEIGHT:I

.field private SURFACE_DETAILS_WIDTH:I

.field private final TAG:Ljava/lang/String;

.field private TIME_FOR_CPU_GPU_MAX_LOCK:I

.field private TOUCH_EXPONENT:F

.field private TOUCH_FRESENL:F

.field private TOUCH_SPECULAR:F

.field TexMoveU:F

.field TiltStartU:F

.field private VCOUNT:I

.field XRatioAdjustLandscape:F

.field XRatioAdjustPortrait:F

.field XRatioForLandscape:F

.field XRatioForPortrait:F

.field YRatioForLandscape:F

.field YRatioForPortrait:F

.field alphaRatio1:F

.field alphaRatio2:F

.field private animationSpeed:F

.field bGravityDirection:Z

.field bitmapBG:Landroid/graphics/Bitmap;

.field bitmapCaustics:Landroid/graphics/Bitmap;

.field bitmapCaustics2:Landroid/graphics/Bitmap;

.field bitmapGravity:Landroid/graphics/Bitmap;

.field bitmapWater:Landroid/graphics/Bitmap;

.field private calledScreenTurnedOn:Z

.field causticsTimeMix:F

.field causticsTimeRatio:F

.field causticsTimeRatio2:F

.field cpuMaxClockBooster:Landroid/os/DVFSHelper;

.field private defaultX:F

.field private defaultY:F

.field private diffPressTime:J

.field private downX:F

.field private downY:F

.field private drawCount:I

.field fWaterBrightness:F

.field glX:F

.field glY:F

.field private gpuHeights:[F

.field gpuMaxFreqBooster:Landroid/os/DVFSHelper;

.field private gravityEffectType:I

.field private heights:[F

.field private heightsSub1:[F

.field private heightsSub2:[F

.field private hoverPlus_Frenel:F

.field private hoverPlus_Specular:F

.field private hoverPlus_exponent:F

.field private indices:[S

.field private inkColorFromSetting:[[F

.field intensityForLandscape:F

.field intensityForPortrait:F

.field intensityForRipple:F

.field private isFirstTouched:Z

.field private isInkEnable:Z

.field private isMakedASpenToucdUp:Z

.field private isOrientationChangCount:I

.field private isOrientationChanged:Z

.field private isPrevSurfaceWidth:I

.field private isRestrictCPUClock:Z

.field private isRestrictGPUFreq:Z

.field private isScreenTurnedOn:Z

.field private isShowCalled:Z

.field private isSurfaceChanged:Z

.field private isSystemSoundChecked:Z

.field isTouched:Z

.field private isUseLCD:Z

.field private is_JBP_Upgrade:Z

.field keyguardManager:Landroid/app/KeyguardManager;

.field leftDirectionTilt:F

.field mBgChangeCheckArray:Ljava/util/ArrayList;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Ljava/util/ArrayList",
            "<",
            "Ljava/lang/Boolean;",
            ">;"
        }
    .end annotation
.end field

.field private mBitmapRatio:F

.field private mBottomWaveReductionRate:F

.field private mBottomWaveTime:J

.field private mBottomWaveVelocity:F

.field private mContext:Landroid/content/Context;

.field private mDefaultRunnable:Ljava/lang/Runnable;

.field private mDrawEffectFrameCnt:I

.field private final mDrawTickTerm:I

.field private mEnableArcMotion:Z

.field private mExponentRatio:F

.field private mFresnelRatio:F

.field private final mGravityHeightWeight:F

.field private mHoverEnabled:Z

.field private mHoverIntensity:F

.field private mInkEffectColor:I

.field private mLandscape:Z

.field private mLightHeight:F

.field private mLongPressRunnable:Ljava/lang/Runnable;

.field mParent:Landroid/view/View;

.field private mPreviousRippleTime:J

.field private mRDownId:I

.field private mRUpId:I

.field private mReductionRate:F

.field private mReductionRateSub:F

.field mRunDirectionAni:Z

.field private final mSContextManager:Landroid/hardware/scontext/SContextManager;

.field private mScreenHeight:I

.field private mScreenWidth:I

.field private mSelectEffect:I

.field private mSoundPool:Landroid/media/SoundPool;

.field private mSoundPool_Gravity:Landroid/media/SoundPool;

.field private mSpecularRatio:F

.field private mSubWaveTime:J

.field private mTranslateX:F

.field private mTranslateY:F

.field private mTranslateZ:F

.field private mWallpaperPath:Ljava/lang/String;

.field private mWaveEnable:Z

.field private mWaveVelocity:F

.field private max:I

.field private mouseInputCount:I

.field private mouseX:F

.field private mouseY:F

.field private moveCount:I

.field private prevPressTime:J

.field private proj:[F

.field reflectionRatio:F

.field refractiveIndex:F

.field rightDirectionTilt:F

.field private rippleDistance:I

.field rippleDragThreshold:D

.field private soundNum:I

.field private soundTime:I

.field private sounds:[I

.field private sounds_gravity:[I

.field spanXForLandscape:I

.field spanXForPortrait:I

.field spanYForLandscape:I

.field spanYForPortrait:I

.field supportedCPUClockTable:[I

.field supportedGPUFreqTable:[I

.field textures0:[I

.field textures1:[I

.field tmx:F

.field tmy:F

.field translateXForLandscape:F

.field translateXForPortrait:F

.field translateYForLandscape:F

.field translateYForPortrait:F

.field translateZForLandscape:F

.field translateZForPortrait:F

.field unitCellHeight:F

.field unitCellWidth:F

.field private velocity:[F

.field private velocitySub1:[F

.field private velocitySub2:[F

.field private vertices:[F

.field private view:[F

.field windowHeight:I

.field windowWidth:I

.field private world:[F

.field private wv:[F

.field private wvp:[F


# direct methods
.method static constructor <clinit>()V
    .registers 1

    .prologue
    .line 424
    const/4 v0, 0x0

    sput-object v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    return-void
.end method

.method public constructor <init>(Landroid/content/Context;Landroid/view/View;II)V
    .registers 13
    .param p1, "context"    # Landroid/content/Context;
    .param p2, "view"    # Landroid/view/View;
    .param p3, "width"    # I
    .param p4, "height"    # I

    .prologue
    const/high16 v7, 0x3f800000    # 1.0f

    const/4 v6, 0x1

    const/4 v1, 0x0

    const/4 v5, 0x0

    const/4 v4, 0x0

    .line 434
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 105
    iput-boolean v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->DBG:Z

    .line 106
    const-string v0, "CircleUnlockRippleRenderer"

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TAG:Ljava/lang/String;

    .line 109
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    .line 110
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    .line 112
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    .line 113
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    .line 115
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    .line 116
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    .line 118
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->max:I

    .line 119
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->VCOUNT:I

    .line 121
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->unitCellWidth:F

    .line 122
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->unitCellHeight:F

    .line 124
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heights:[F

    .line 125
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocity:[F

    .line 127
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    .line 128
    new-array v0, v4, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    .line 130
    new-array v0, v4, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    .line 131
    new-array v0, v4, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub2:[F

    .line 134
    new-array v0, v4, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->vertices:[F

    .line 135
    new-array v0, v4, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    .line 136
    new-array v0, v4, [S

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->indices:[S

    .line 138
    new-array v0, v4, [I

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->textures0:[I

    .line 139
    new-array v0, v4, [I

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->textures1:[I

    .line 142
    const/16 v0, 0x10

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->view:[F

    .line 143
    const/16 v0, 0x10

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->proj:[F

    .line 144
    const/16 v0, 0x10

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->world:[F

    .line 145
    const/16 v0, 0x10

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    .line 146
    const/16 v0, 0x10

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    .line 148
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    .line 150
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    .line 151
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    .line 153
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    .line 154
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseY:F

    .line 157
    const/high16 v0, 0x3f000000    # 0.5f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mWaveVelocity:F

    .line 158
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mWaveEnable:Z

    .line 161
    const v0, 0x3f70a3d7    # 0.94f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->REDUCTION_RATE_NORMAL:F

    .line 162
    const v0, 0x3f75c28f    # 0.96f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->REDUCTION_RATE_RAIN:F

    .line 163
    const v0, 0x3f70a3d7    # 0.94f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->REDUCTION_RATE_WAVE:F

    .line 164
    iput v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->REDUCTION_RATE_WAVE2:F

    .line 167
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBitmapRatio:F

    .line 170
    const v0, 0x3dcccccd    # 0.1f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_FRESENL:F

    .line 171
    const/high16 v0, 0x3f000000    # 0.5f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_SPECULAR:F

    .line 172
    const/high16 v0, 0x41a00000    # 20.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_EXPONENT:F

    .line 174
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_FRESENL:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mFresnelRatio:F

    .line 175
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_SPECULAR:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSpecularRatio:F

    .line 176
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_EXPONENT:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mExponentRatio:F

    .line 179
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_FRESENL:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_FRESENL_MIN:F

    .line 180
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_SPECULAR:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_SPECULAR_RATIO_MIN:F

    .line 181
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_EXPONENT:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_EXPONENT_RATIO_MIN:F

    .line 183
    iput v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_FRESENL_MAX:F

    .line 184
    const/high16 v0, 0x41200000    # 10.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_SPECULAR_RATIO_MAX:F

    .line 185
    const/high16 v0, 0x41a00000    # 20.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_EXPONENT_RATIO_MAX:F

    .line 186
    const v0, 0x3ccccccd    # 0.025f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_INTENSITY_MAX:F

    .line 187
    const v0, 0x3c23d70a    # 0.01f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->hoverPlus_Frenel:F

    .line 188
    const v0, 0x3dcccccd    # 0.1f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->hoverPlus_Specular:F

    .line 189
    const v0, 0x3dcccccd    # 0.1f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->hoverPlus_exponent:F

    .line 190
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_INTENSITY_MAX:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mHoverIntensity:F

    .line 191
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mHoverEnabled:Z

    .line 192
    const-wide/16 v2, 0x0

    iput-wide v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mPreviousRippleTime:J

    .line 193
    const-wide/16 v2, 0x640

    iput-wide v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->RIPPLE_WAIT_TIME:J

    .line 196
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLandscape:Z

    .line 199
    const v0, 0x3f70a3d7    # 0.94f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mReductionRate:F

    .line 200
    const v0, 0x3f7d70a4    # 0.99f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mReductionRateSub:F

    .line 201
    const-wide/16 v2, 0x0

    iput-wide v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSubWaveTime:J

    .line 204
    const/high16 v0, 0x3fc00000    # 1.5f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLightHeight:F

    .line 206
    const v0, 0x3f6e147b    # 0.93f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->refractiveIndex:F

    .line 207
    const v0, 0x3e051eb8    # 0.13f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->reflectionRatio:F

    .line 208
    iput v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->alphaRatio1:F

    .line 209
    iput v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->alphaRatio2:F

    .line 214
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    .line 215
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapWater:Landroid/graphics/Bitmap;

    .line 217
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->tmx:F

    .line 218
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->tmy:F

    .line 220
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    .line 221
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    .line 224
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 225
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    .line 227
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeMix:F

    .line 228
    const v0, 0x3dcccccd    # 0.1f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MarkcuasticsTMix:F

    .line 229
    const/high16 v0, 0x42200000    # 40.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ReferencePoint:F

    .line 230
    const/high16 v0, 0x3f000000    # 0.5f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TexMoveU:F

    .line 231
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bGravityDirection:Z

    .line 232
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    .line 233
    const/4 v0, -0x1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CurrentState:I

    .line 234
    iput v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    .line 243
    const/high16 v0, 0x3f000000    # 0.5f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForLandscape:F

    .line 244
    iput v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForPortrait:F

    .line 248
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForLandscape:F

    .line 249
    const/high16 v0, -0x80000000

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForPortrait:F

    .line 252
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForLandscape:F

    .line 253
    const/high16 v0, -0x80000000

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForPortrait:F

    .line 256
    const/high16 v0, -0x3dd00000    # -44.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForPortrait:F

    .line 257
    const/high16 v0, -0x3e480000    # -23.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForLandscape:F

    .line 260
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForLandscape:I

    .line 261
    const/16 v0, 0x10

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForLandscape:I

    .line 262
    const/16 v0, 0x10

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForPortrait:I

    .line 263
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForPortrait:I

    .line 266
    const/high16 v0, 0x42340000    # 45.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForLandscape:F

    .line 267
    const/high16 v0, 0x41c80000    # 25.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForLandscape:F

    .line 268
    const/high16 v0, 0x41c80000    # 25.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForPortrait:F

    .line 269
    const/high16 v0, 0x42380000    # 46.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForPortrait:F

    .line 270
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustPortrait:F

    .line 271
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustLandscape:F

    .line 276
    iput v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForRipple:F

    .line 277
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    .line 278
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    .line 281
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->downX:F

    .line 282
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->downY:F

    .line 283
    const-wide v2, 0x4062c00000000000L    # 150.0

    iput-wide v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rippleDragThreshold:D

    .line 284
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rippleDistance:I

    .line 285
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseInputCount:I

    .line 288
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    .line 289
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool_Gravity:Landroid/media/SoundPool;

    .line 290
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds:[I

    .line 291
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds_gravity:[I

    .line 292
    const-wide/16 v2, 0x0

    iput-wide v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->diffPressTime:J

    .line 293
    const-wide/16 v2, 0x0

    iput-wide v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->prevPressTime:J

    .line 294
    const/4 v0, 0x5

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->soundNum:I

    .line 295
    const/16 v0, 0xa

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->soundTime:I

    .line 296
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SOUND_ID_DOWN:I

    .line 297
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SOUND_ID_UP:I

    .line 298
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SOUND_ID_GRAVITY:I

    .line 299
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRDownId:I

    .line 300
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRUpId:I

    .line 301
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->drawCount:I

    .line 302
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->moveCount:I

    .line 303
    iput-boolean v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isSystemSoundChecked:Z

    .line 307
    iput-boolean v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isFirstTouched:Z

    .line 308
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->calledScreenTurnedOn:Z

    .line 309
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isScreenTurnedOn:Z

    .line 312
    iput-boolean v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isMakedASpenToucdUp:Z

    .line 314
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->defaultX:F

    .line 315
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->defaultY:F

    .line 329
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->is_JBP_Upgrade:Z

    .line 330
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isInkEnable:Z

    .line 331
    iput-boolean v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isUseLCD:Z

    move-object v0, v1

    .line 336
    check-cast v0, [[F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    .line 337
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    .line 338
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isTouched:Z

    .line 343
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBgChangeCheckArray:Ljava/util/ArrayList;

    .line 348
    const/16 v0, 0x2f0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HDMI_WIDOW_HEIGHT_FOR_P4_NOTE_10_1:I

    .line 349
    const/16 v0, 0x2d0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HDMI_VIEW_HEIGHT_FOR_P4_NOTE_10_1:I

    .line 356
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapGravity:Landroid/graphics/Bitmap;

    .line 357
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics:Landroid/graphics/Bitmap;

    .line 358
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics2:Landroid/graphics/Bitmap;

    .line 360
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->GRAVITY_EFFECT_LEFT:I

    .line 361
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->GRAVITY_EFFECT_RIGHT:I

    .line 363
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mEnableArcMotion:Z

    .line 368
    const/4 v0, -0x1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    .line 372
    const/4 v0, -0x1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gravityEffectType:I

    .line 374
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    .line 375
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    .line 376
    const v0, 0x3d8f5c29    # 0.07f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TiltStartU:F

    .line 378
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    .line 380
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateX:F

    .line 381
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateY:F

    .line 382
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateZ:F

    .line 384
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDrawEffectFrameCnt:I

    .line 385
    const/16 v0, 0x1e

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDrawTickTerm:I

    .line 386
    const-wide/16 v2, 0x0

    iput-wide v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveTime:J

    .line 388
    const v0, 0x3e99999a    # 0.3f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveVelocity:F

    .line 389
    const/high16 v0, 0x40400000    # 3.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mGravityHeightWeight:F

    .line 391
    const v0, 0x3f70a3d7    # 0.94f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveReductionRate:F

    .line 394
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isShowCalled:Z

    .line 395
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChanged:Z

    .line 396
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChangCount:I

    .line 397
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isSurfaceChanged:Z

    .line 398
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isPrevSurfaceWidth:I

    .line 401
    const/4 v0, -0x1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CPU_CLOCK_NUM:I

    .line 402
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    .line 403
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedCPUClockTable:[I

    .line 404
    iput-boolean v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    .line 407
    const/4 v0, -0x1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->GPU_FREQUNCY_NUM:I

    .line 408
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    .line 409
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedGPUFreqTable:[I

    .line 410
    iput-boolean v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    .line 412
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CPU_CLOK_CONTROL:I

    .line 413
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->GPU_FREQ_CONTROL:I

    .line 415
    const v0, 0x88b8

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TIME_FOR_CPU_GPU_MAX_LOCK:I

    .line 417
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ACQUIRE_DVFS:I

    .line 418
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->RELEASE_DVFS:I

    .line 419
    const/4 v0, 0x2

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CREATEDED_CPU:I

    .line 420
    const/4 v0, 0x3

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CREATEDED_GPU:I

    .line 421
    const/4 v0, 0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->DESTROYED_CPU:I

    .line 422
    const/4 v0, 0x5

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->DESTROYED_GPU:I

    .line 436
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "CircleUnlockRippleRenderer Constructor"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 438
    iput-object p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    .line 439
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    const-string v1, "keyguard"

    invoke-virtual {v0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/KeyguardManager;

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->keyguardManager:Landroid/app/KeyguardManager;

    .line 441
    iput-object p2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    .line 443
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    sget v1, Lcom/codex/s4unlockfx/R$bool;->s3_config_is_jbp_upgrade:I

    invoke-virtual {v0, v1}, Landroid/content/res/Resources;->getBoolean(I)Z

    move-result v0

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->is_JBP_Upgrade:Z

    .line 444
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    sget v1, Lcom/codex/s4unlockfx/R$bool;->s3_config_is_water_ink_enabled:I

    invoke-virtual {v0, v1}, Landroid/content/res/Resources;->getBoolean(I)Z

    move-result v0

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isInkEnable:Z

    .line 445
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    sget v1, Lcom/codex/s4unlockfx/R$bool;->s3_config_is_water_ink_lcd:I

    invoke-virtual {v0, v1}, Landroid/content/res/Resources;->getBoolean(I)Z

    move-result v0

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isUseLCD:Z

    .line 447
    const-string v0, "CircleUnlockRippleRenderer"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "is_JBP_Upgrade = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->is_JBP_Upgrade:Z

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 448
    const-string v0, "CircleUnlockRippleRenderer"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "isInkEnable = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isInkEnable:Z

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 449
    const-string v0, "CircleUnlockRippleRenderer"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "isUseLCD = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isUseLCD:Z

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 451
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isUseLCD:Z

    if-eqz v0, :cond_3ae

    .line 453
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->inkColorFromSettingForLCD:[[F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    .line 461
    :goto_2ca
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    sget v1, Lcom/codex/s4unlockfx/R$bool;->s3_restrict_cpu_clock_ripple:I

    invoke-virtual {v0, v1}, Landroid/content/res/Resources;->getBoolean(I)Z

    move-result v0

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    .line 462
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    sget v1, Lcom/codex/s4unlockfx/R$integer;->s3_cpu_clock_index_for_ripple:I

    invoke-virtual {v0, v1}, Landroid/content/res/Resources;->getInteger(I)I

    move-result v0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CPU_CLOCK_NUM:I

    .line 463
    const-string v0, "CircleUnlockRippleRenderer"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "== DVFS == isRestrictCPUClock = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 464
    const-string v0, "CircleUnlockRippleRenderer"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "== DVFS == CPU_CLOCK_NUM = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CPU_CLOCK_NUM:I

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 467
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    sget v1, Lcom/codex/s4unlockfx/R$bool;->s3_restrict_gpu_freq_ripple:I

    invoke-virtual {v0, v1}, Landroid/content/res/Resources;->getBoolean(I)Z

    move-result v0

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    .line 468
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    sget v1, Lcom/codex/s4unlockfx/R$integer;->s3_gpu_freq_index_for_ripple:I

    invoke-virtual {v0, v1}, Landroid/content/res/Resources;->getInteger(I)I

    move-result v0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->GPU_FREQUNCY_NUM:I

    .line 469
    const-string v0, "CircleUnlockRippleRenderer"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "== DVFS == isRestrictGPUFreq = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 470
    const-string v0, "CircleUnlockRippleRenderer"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "== DVFS == GPU_FREQUNCY_NUM = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->GPU_FREQUNCY_NUM:I

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 472
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    if-nez v0, :cond_376

    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_388

    .line 474
    :cond_376
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    if-nez v0, :cond_388

    .line 476
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "== DVFS == new DVFSHandlerRipple"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 477
    new-instance v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    invoke-direct {v0, p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;-><init>(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)V

    sput-object v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    .line 481
    :cond_388
    iput p3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    .line 482
    iput p4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    .line 484
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setModeleConfiguration()V

    .line 486
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->initWaters()V

    .line 488
    new-instance v0, Ljava/util/ArrayList;

    invoke-direct {v0}, Ljava/util/ArrayList;-><init>()V

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBgChangeCheckArray:Ljava/util/ArrayList;

    .line 490
    invoke-direct {p0, v6}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setBackground(Z)V

    .line 492
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    const-string v1, "scontext"

    invoke-virtual {v0, v1}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/hardware/scontext/SContextManager;

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSContextManager:Landroid/hardware/scontext/SContextManager;

    .line 493
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setSound_gravity()V

    .line 494
    return-void

    .line 457
    :cond_3ae
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->inkColorFromSettingForLED:[[F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    goto/16 :goto_2ca
.end method

.method static synthetic access$000(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;FFFZ)V
    .registers 5
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;
    .param p1, "x1"    # F
    .param p2, "x2"    # F
    .param p3, "x3"    # F
    .param p4, "x4"    # Z

    .prologue
    .line 100
    invoke-direct {p0, p1, p2, p3, p4}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ripple(FFFZ)V

    return-void
.end method

.method static synthetic access$1000(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)F
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->defaultX:F

    return v0
.end method

.method static synthetic access$102(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;Ljava/lang/Runnable;)Ljava/lang/Runnable;
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;
    .param p1, "x1"    # Ljava/lang/Runnable;

    .prologue
    .line 100
    iput-object p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLongPressRunnable:Ljava/lang/Runnable;

    return-object p1
.end method

.method static synthetic access$1100(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    return v0
.end method

.method static synthetic access$1200(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    return v0
.end method

.method static synthetic access$1300(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)F
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->defaultY:F

    return v0
.end method

.method static synthetic access$1400(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;Z)V
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;
    .param p1, "x1"    # Z

    .prologue
    .line 100
    invoke-direct {p0, p1}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setHoverEnable(Z)V

    return-void
.end method

.method static synthetic access$1502(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;Ljava/lang/Runnable;)Ljava/lang/Runnable;
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;
    .param p1, "x1"    # Ljava/lang/Runnable;

    .prologue
    .line 100
    iput-object p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDefaultRunnable:Ljava/lang/Runnable;

    return-object p1
.end method

.method static synthetic access$1600(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)V
    .registers 1
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setFalseDefaultEffectFlag()V

    return-void
.end method

.method static synthetic access$1700(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)V
    .registers 1
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->aquireCpuGpuMaxLock()V

    return-void
.end method

.method static synthetic access$1800(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)V
    .registers 1
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->releaseCpuGpuMaxLock()V

    return-void
.end method

.method static synthetic access$1900(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)Landroid/content/Context;
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    return-object v0
.end method

.method static synthetic access$200(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->soundNum:I

    return v0
.end method

.method static synthetic access$300(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)Z
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isSystemSoundChecked:Z

    return v0
.end method

.method static synthetic access$400(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)Landroid/media/SoundPool;
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    return-object v0
.end method

.method static synthetic access$500(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->soundTime:I

    return v0
.end method

.method static synthetic access$600(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)Z
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    return v0
.end method

.method static synthetic access$700(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)Z
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    return v0
.end method

.method static synthetic access$800()Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;
    .registers 1

    .prologue
    .line 100
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    return-object v0
.end method

.method static synthetic access$900(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)Z
    .registers 2
    .param p0, "x0"    # Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .prologue
    .line 100
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLandscape:Z

    return v0
.end method

.method private acquireBooster(I)V
    .registers 8
    .param p1, "type"    # I

    .prologue
    .line 2793
    const/4 v0, 0x0

    .line 2794
    .local v0, "bestCpuClock":I
    const/4 v1, 0x0

    .line 2796
    .local v1, "bestGpuFreq":I
    if-nez p1, :cond_75

    .line 2798
    const-string v2, "CircleUnlockRippleRenderer"

    const-string v3, "== DVFS == acquireBooster CPU_CLOK_CONTROL"

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2800
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    if-eqz v2, :cond_6d

    .line 2802
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedCPUClockTable:[I

    if-nez v2, :cond_65

    .line 2804
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    invoke-virtual {v2}, Landroid/os/DVFSHelper;->getSupportedCPUFrequency()[I

    move-result-object v2

    iput-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedCPUClockTable:[I

    .line 2806
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedCPUClockTable:[I

    if-eqz v2, :cond_5d

    .line 2808
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedCPUClockTable:[I

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CPU_CLOCK_NUM:I

    invoke-direct {p0, v2, v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->getBestMaxFreq([II)I

    move-result v0

    .line 2809
    const-string v2, "CircleUnlockRippleRenderer"

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "== DVFS == acquire!!! CPU, ["

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedCPUClockTable:[I

    aget v4, v4, v0

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v4, "]"

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2810
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    const-string v3, "CPU"

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedCPUClockTable:[I

    aget v4, v4, v0

    int-to-long v4, v4

    invoke-virtual {v2, v3, v4, v5}, Landroid/os/DVFSHelper;->addExtraOption(Ljava/lang/String;J)V

    .line 2811
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TIME_FOR_CPU_GPU_MAX_LOCK:I

    invoke-virtual {v2, v3}, Landroid/os/DVFSHelper;->acquire(I)V

    .line 2864
    :goto_5c
    return-void

    .line 2815
    :cond_5d
    const-string v2, "CircleUnlockRippleRenderer"

    const-string v3, "== DVFS == Fail getSupportedCPUFrequency! Not Support a CPU Clock Table."

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_5c

    .line 2820
    :cond_65
    const-string v2, "CircleUnlockRippleRenderer"

    const-string v3, "== DVFS == Not Acquire!!! It\'s already acquired."

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_5c

    .line 2825
    :cond_6d
    const-string v2, "CircleUnlockRippleRenderer"

    const-string v3, "== DVFS == cpuMaxClockBooster is null."

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_5c

    .line 2828
    :cond_75
    const/4 v2, 0x1

    if-ne p1, v2, :cond_eb

    .line 2830
    const-string v2, "CircleUnlockRippleRenderer"

    const-string v3, "== DVFS == acquireBooster GPU_FREQ_CONTROL"

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2832
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    if-eqz v2, :cond_e2

    .line 2834
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedGPUFreqTable:[I

    if-nez v2, :cond_d9

    .line 2836
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    invoke-virtual {v2}, Landroid/os/DVFSHelper;->getSupportedGPUFrequency()[I

    move-result-object v2

    iput-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedGPUFreqTable:[I

    .line 2838
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedGPUFreqTable:[I

    if-eqz v2, :cond_d1

    .line 2840
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedGPUFreqTable:[I

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->GPU_FREQUNCY_NUM:I

    invoke-direct {p0, v2, v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->getBestMaxFreq([II)I

    move-result v1

    .line 2841
    const-string v2, "CircleUnlockRippleRenderer"

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "== DVFS == acquire!!! GPU, ["

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedGPUFreqTable:[I

    aget v4, v4, v1

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v4, "]"

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2842
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    const-string v3, "GPU"

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedGPUFreqTable:[I

    aget v4, v4, v1

    int-to-long v4, v4

    invoke-virtual {v2, v3, v4, v5}, Landroid/os/DVFSHelper;->addExtraOption(Ljava/lang/String;J)V

    .line 2843
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TIME_FOR_CPU_GPU_MAX_LOCK:I

    invoke-virtual {v2, v3}, Landroid/os/DVFSHelper;->acquire(I)V

    goto :goto_5c

    .line 2847
    :cond_d1
    const-string v2, "CircleUnlockRippleRenderer"

    const-string v3, "== DVFS == Fail getSupportedGPUFrequency! Not Support GPU Freq Table."

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_5c

    .line 2852
    :cond_d9
    const-string v2, "CircleUnlockRippleRenderer"

    const-string v3, "== DVFS == Not Acquire! It\'s alreay aquired"

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto/16 :goto_5c

    .line 2857
    :cond_e2
    const-string v2, "CircleUnlockRippleRenderer"

    const-string v3, "== DVFS == gpuMaxFreqBooster is null"

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto/16 :goto_5c

    .line 2862
    :cond_eb
    const-string v2, "CircleUnlockRippleRenderer"

    const-string v3, "== DVFS == type is invalid."

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto/16 :goto_5c
.end method

.method private aquireCpuGpuMaxLock()V
    .registers 2

    .prologue
    .line 2767
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    if-eqz v0, :cond_8

    .line 2769
    const/4 v0, 0x0

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->acquireBooster(I)V

    .line 2772
    :cond_8
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_10

    .line 2774
    const/4 v0, 0x1

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->acquireBooster(I)V

    .line 2776
    :cond_10
    return-void
.end method

.method private checkSound()V
    .registers 7

    .prologue
    const/4 v5, 0x1

    .line 1735
    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v3}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    .line 1736
    .local v0, "cr":Landroid/content/ContentResolver;
    const/4 v2, 0x0

    .line 1740
    .local v2, "result":I
    :try_start_8
    const-string v3, "lockscreen_sounds_enabled"

    const/4 v4, 0x0

    invoke-static {v0, v3, v4}, Landroid/provider/Settings$System;->getIntForUser(Landroid/content/ContentResolver;Ljava/lang/String;I)I
    :try_end_e
    .catch Landroid/provider/Settings$SettingNotFoundException; {:try_start_8 .. :try_end_e} :catch_14

    move-result v2

    .line 1748
    :goto_f
    if-ne v2, v5, :cond_19

    .line 1749
    iput-boolean v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isSystemSoundChecked:Z

    .line 1753
    :goto_13
    return-void

    .line 1742
    :catch_14
    move-exception v1

    .line 1744
    .local v1, "e":Landroid/provider/Settings$SettingNotFoundException;
    invoke-virtual {v1}, Ljava/lang/Throwable;->printStackTrace()V

    goto :goto_f

    .line 1751
    .end local v1    # "e":Landroid/provider/Settings$SettingNotFoundException;
    :cond_19
    const/4 v3, 0x0

    iput-boolean v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isSystemSoundChecked:Z

    goto :goto_13
.end method

.method private getBestMaxFreq([II)I
    .registers 9
    .param p1, "pArray"    # [I
    .param p2, "bestValue"    # I

    .prologue
    .line 2914
    const/4 v4, 0x0

    .line 2915
    .local v4, "value":I
    const v3, 0x7fffffff

    .line 2916
    .local v3, "prevdiff":I
    const/4 v1, 0x0

    .line 2917
    .local v1, "currdiff":I
    array-length v0, p1

    .line 2919
    .local v0, "arrayLenth":I
    const/4 v2, 0x0

    .local v2, "i":I
    :goto_7
    if-ge v2, v0, :cond_18

    .line 2922
    aget v5, p1, v2

    sub-int v5, p2, v5

    invoke-static {v5}, Ljava/lang/Math;->abs(I)I

    move-result v1

    .line 2923
    if-ge v1, v3, :cond_15

    .line 2925
    move v4, v2

    .line 2926
    move v3, v1

    .line 2919
    :cond_15
    add-int/lit8 v2, v2, 0x1

    goto :goto_7

    .line 2930
    :cond_18
    return v4
.end method

.method private initWaters()V
    .registers 9

    .prologue
    const/4 v7, 0x0

    .line 1460
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "initWaters"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1462
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->VCOUNT:I

    mul-int/lit8 v0, v0, 0x3

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->vertices:[F

    .line 1463
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    add-int/lit8 v0, v0, -0x1

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    add-int/lit8 v1, v1, -0x1

    mul-int/2addr v0, v1

    mul-int/lit8 v0, v0, 0x6

    new-array v0, v0, [S

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->indices:[S

    .line 1465
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->max:I

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heights:[F

    .line 1466
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->max:I

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocity:[F

    .line 1467
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->max:I

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    .line 1468
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->max:I

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    .line 1469
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->max:I

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    .line 1470
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->max:I

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub2:[F

    .line 1472
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->VCOUNT:I

    mul-int/lit8 v0, v0, 0x3

    new-array v0, v0, [F

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    .line 1474
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->vertices:[F

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->indices:[S

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->VCOUNT:I

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    iget v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    iget v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    iget v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    invoke-static/range {v0 .. v6}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->initWaters([F[SIIIII)V

    .line 1479
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heights:[F

    invoke-static {v0, v7}, Ljava/util/Arrays;->fill([FF)V

    .line 1480
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocity:[F

    invoke-static {v0, v7}, Ljava/util/Arrays;->fill([FF)V

    .line 1481
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    invoke-static {v0, v7}, Ljava/util/Arrays;->fill([FF)V

    .line 1482
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    invoke-static {v0, v7}, Ljava/util/Arrays;->fill([FF)V

    .line 1483
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    invoke-static {v0, v7}, Ljava/util/Arrays;->fill([FF)V

    .line 1484
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub2:[F

    invoke-static {v0, v7}, Ljava/util/Arrays;->fill([FF)V

    .line 1485
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    invoke-static {v0, v7}, Ljava/util/Arrays;->fill([FF)V

    .line 1486
    return-void
.end method

.method private loadBitmapIfBitmapNull()V
    .registers 3

    .prologue
    .line 1841
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapWater:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_16

    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_16

    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v0, v1, :cond_4c

    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapGravity:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_16

    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics:Landroid/graphics/Bitmap;

    if-nez v0, :cond_4c

    .line 1843
    :cond_16
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapWater:Landroid/graphics/Bitmap;

    if-nez v0, :cond_21

    .line 1845
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "bitmapWater == null"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1848
    :cond_21
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    if-nez v0, :cond_2c

    .line 1850
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "bitmapBG == null"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1853
    :cond_2c
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v0, v1, :cond_48

    .line 1854
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapGravity:Landroid/graphics/Bitmap;

    if-nez v0, :cond_3d

    .line 1855
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "bitmapGravity == null"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1858
    :cond_3d
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics:Landroid/graphics/Bitmap;

    if-nez v0, :cond_48

    .line 1859
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "bitmapCaustics == null"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1863
    :cond_48
    const/4 v0, 0x1

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setBackground(Z)V

    .line 1865
    :cond_4c
    return-void
.end method

.method private move()V
    .registers 19

    .prologue
    .line 1493
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heights:[F

    if-eqz v1, :cond_2a

    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocity:[F

    if-eqz v1, :cond_2a

    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    if-eqz v1, :cond_2a

    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    if-eqz v1, :cond_2a

    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    if-eqz v1, :cond_2a

    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub2:[F

    if-eqz v1, :cond_2a

    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    if-nez v1, :cond_32

    .line 1502
    :cond_2a
    const-string v1, "CircleUnlockRippleRenderer"

    const-string v2, "initWaters don\'t called"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1603
    :cond_31
    return-void

    .line 1507
    :cond_32
    const/4 v4, 0x1

    .line 1508
    .local v4, "xSpan":I
    const/4 v3, 0x1

    .line 1510
    .local v3, "ySpan":I
    move-object/from16 v0, p0

    iget-boolean v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLandscape:Z

    if-eqz v1, :cond_1dd

    .line 1511
    move-object/from16 v0, p0

    iget v4, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForLandscape:I

    .line 1512
    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForLandscape:I

    .line 1513
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    sub-int v5, v1, v3

    .line 1514
    .local v5, "imax":I
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    sub-int v6, v1, v4

    .line 1522
    .local v6, "jmax":I
    :goto_4e
    const/4 v15, 0x1

    .line 1523
    .local v15, "result1":I
    const/16 v16, 0x1

    .line 1525
    .local v16, "result2":I
    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v1, v2, :cond_96

    .line 1527
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    move-object/from16 v0, p0

    iget v7, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    move-object/from16 v0, p0

    iget v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    const/4 v9, 0x1

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveReductionRate:F

    move-object/from16 v0, p0

    iget v11, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveVelocity:F

    invoke-static/range {v1 .. v11}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->move([F[FIIIIIIZFF)I

    move-result v15

    .line 1531
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub2:[F

    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    move-object/from16 v0, p0

    iget v7, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    move-object/from16 v0, p0

    iget v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    const/4 v9, 0x1

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveReductionRate:F

    move-object/from16 v0, p0

    iget v11, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveVelocity:F

    const v17, 0x3f333333    # 0.7f

    mul-float v11, v11, v17

    invoke-static/range {v1 .. v11}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->move([F[FIIIIIIZFF)I

    move-result v16

    .line 1537
    :cond_96
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocity:[F

    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heights:[F

    move-object/from16 v0, p0

    iget v7, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    move-object/from16 v0, p0

    iget v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    const/4 v9, 0x1

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mReductionRate:F

    const/high16 v11, 0x3f000000    # 0.5f

    invoke-static/range {v1 .. v11}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->move([F[FIIIIIIZFF)I

    move-result v1

    if-eqz v1, :cond_d3

    if-eqz v15, :cond_d3

    if-eqz v16, :cond_d3

    .line 1541
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->drawCount:I

    const/4 v2, 0x2

    if-lt v1, v2, :cond_d3

    .line 1543
    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-eq v1, v2, :cond_ca

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v1, v2, :cond_1f3

    .line 1545
    :cond_ca
    move-object/from16 v0, p0

    iget-boolean v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isTouched:Z

    if-nez v1, :cond_d3

    .line 1547
    invoke-direct/range {p0 .. p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setRenderModeDirty()V

    .line 1560
    :cond_d3
    :goto_d3
    const/4 v12, 0x0

    .local v12, "i":I
    :goto_d4
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    if-ge v12, v1, :cond_31

    .line 1561
    const/4 v14, 0x0

    .local v14, "j":I
    :goto_db
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    if-ge v14, v1, :cond_2b8

    .line 1562
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    mul-int/2addr v1, v14

    add-int/2addr v1, v12

    mul-int/lit8 v13, v1, 0x3

    .line 1563
    .local v13, "idx":I
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x0

    move-object/from16 v0, p0

    iget-object v7, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heights:[F

    add-int/lit8 v8, v14, 0x2

    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v8, v9

    add-int/2addr v8, v12

    add-int/lit8 v8, v8, 0x2

    aget v7, v7, v8

    aput v7, v1, v2

    .line 1565
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x1

    move-object/from16 v0, p0

    iget-object v7, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heights:[F

    add-int/lit8 v8, v14, 0x2

    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v8, v9

    add-int/2addr v8, v12

    add-int/lit8 v8, v8, 0x1

    aget v7, v7, v8

    aput v7, v1, v2

    .line 1567
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x2

    move-object/from16 v0, p0

    iget-object v7, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heights:[F

    add-int/lit8 v8, v14, 0x1

    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v8, v9

    add-int/2addr v8, v12

    add-int/lit8 v8, v8, 0x2

    aget v7, v7, v8

    aput v7, v1, v2

    .line 1570
    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v1, v2, :cond_210

    .line 1571
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x0

    aget v7, v1, v2

    move-object/from16 v0, p0

    iget-object v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    add-int/lit8 v9, v14, 0x2

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v9, v10

    add-int/2addr v9, v12

    add-int/lit8 v9, v9, 0x2

    aget v8, v8, v9

    add-float/2addr v7, v8

    aput v7, v1, v2

    .line 1573
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x1

    aget v7, v1, v2

    move-object/from16 v0, p0

    iget-object v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    add-int/lit8 v9, v14, 0x2

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v9, v10

    add-int/2addr v9, v12

    add-int/lit8 v9, v9, 0x1

    aget v8, v8, v9

    add-float/2addr v7, v8

    aput v7, v1, v2

    .line 1575
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x2

    aget v7, v1, v2

    move-object/from16 v0, p0

    iget-object v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    add-int/lit8 v9, v14, 0x1

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v9, v10

    add-int/2addr v9, v12

    add-int/lit8 v9, v9, 0x2

    aget v8, v8, v9

    add-float/2addr v7, v8

    aput v7, v1, v2

    .line 1578
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x0

    aget v7, v1, v2

    move-object/from16 v0, p0

    iget-object v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    add-int/lit8 v9, v14, 0x2

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v9, v10

    add-int/2addr v9, v12

    add-int/lit8 v9, v9, 0x2

    aget v8, v8, v9

    add-float/2addr v7, v8

    aput v7, v1, v2

    .line 1580
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x1

    aget v7, v1, v2

    move-object/from16 v0, p0

    iget-object v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    add-int/lit8 v9, v14, 0x2

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v9, v10

    add-int/2addr v9, v12

    add-int/lit8 v9, v9, 0x1

    aget v8, v8, v9

    add-float/2addr v7, v8

    aput v7, v1, v2

    .line 1582
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x2

    aget v7, v1, v2

    move-object/from16 v0, p0

    iget-object v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    add-int/lit8 v9, v14, 0x1

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v9, v10

    add-int/2addr v9, v12

    add-int/lit8 v9, v9, 0x2

    aget v8, v8, v9

    add-float/2addr v7, v8

    aput v7, v1, v2

    .line 1561
    :cond_1d9
    :goto_1d9
    add-int/lit8 v14, v14, 0x1

    goto/16 :goto_db

    .line 1516
    .end local v5    # "imax":I
    .end local v6    # "jmax":I
    .end local v12    # "i":I
    .end local v13    # "idx":I
    .end local v14    # "j":I
    .end local v15    # "result1":I
    .end local v16    # "result2":I
    :cond_1dd
    move-object/from16 v0, p0

    iget v4, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForPortrait:I

    .line 1517
    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForPortrait:I

    .line 1518
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    sub-int v5, v1, v3

    .line 1519
    .restart local v5    # "imax":I
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    sub-int v6, v1, v4

    .restart local v6    # "jmax":I
    goto/16 :goto_4e

    .line 1549
    .restart local v15    # "result1":I
    .restart local v16    # "result2":I
    :cond_1f3
    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v1, v2, :cond_20b

    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    const/4 v2, -0x1

    if-ne v1, v2, :cond_20b

    .line 1551
    move-object/from16 v0, p0

    iget-boolean v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isTouched:Z

    if-nez v1, :cond_d3

    .line 1552
    invoke-direct/range {p0 .. p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setRenderModeDirty()V

    goto/16 :goto_d3

    .line 1555
    :cond_20b
    invoke-direct/range {p0 .. p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setRenderModeDirty()V

    goto/16 :goto_d3

    .line 1586
    .restart local v12    # "i":I
    .restart local v13    # "idx":I
    .restart local v14    # "j":I
    :cond_210
    add-int/lit8 v1, v12, -0x7

    if-lez v1, :cond_1d9

    .line 1587
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x0

    aget v7, v1, v2

    move-object/from16 v0, p0

    iget-object v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    add-int/lit8 v9, v14, 0x2

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v9, v10

    add-int/2addr v9, v12

    add-int/lit8 v9, v9, -0x6

    aget v8, v8, v9

    add-float/2addr v7, v8

    aput v7, v1, v2

    .line 1589
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x1

    aget v7, v1, v2

    move-object/from16 v0, p0

    iget-object v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    add-int/lit8 v9, v14, 0x2

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v9, v10

    add-int/2addr v9, v12

    add-int/lit8 v9, v9, -0x7

    aget v8, v8, v9

    add-float/2addr v7, v8

    aput v7, v1, v2

    .line 1591
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x2

    aget v7, v1, v2

    move-object/from16 v0, p0

    iget-object v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    add-int/lit8 v9, v14, 0x1

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v9, v10

    add-int/2addr v9, v12

    add-int/lit8 v9, v9, -0x6

    aget v8, v8, v9

    add-float/2addr v7, v8

    aput v7, v1, v2

    .line 1593
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x0

    aget v7, v1, v2

    move-object/from16 v0, p0

    iget-object v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    add-int/lit8 v9, v14, 0x2

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v9, v10

    add-int/2addr v9, v12

    add-int/lit8 v9, v9, -0x6

    aget v8, v8, v9

    add-float/2addr v7, v8

    aput v7, v1, v2

    .line 1595
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x1

    aget v7, v1, v2

    move-object/from16 v0, p0

    iget-object v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    add-int/lit8 v9, v14, 0x2

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v9, v10

    add-int/2addr v9, v12

    add-int/lit8 v9, v9, -0x7

    aget v8, v8, v9

    add-float/2addr v7, v8

    aput v7, v1, v2

    .line 1597
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    add-int/lit8 v2, v13, 0x2

    aget v7, v1, v2

    move-object/from16 v0, p0

    iget-object v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    add-int/lit8 v9, v14, 0x1

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v9, v10

    add-int/2addr v9, v12

    add-int/lit8 v9, v9, -0x6

    aget v8, v8, v9

    add-float/2addr v7, v8

    aput v7, v1, v2

    goto/16 :goto_1d9

    .line 1560
    .end local v13    # "idx":I
    :cond_2b8
    add-int/lit8 v12, v12, 0x1

    goto/16 :goto_d4
.end method

.method private perspectiveM([FFFFF)V
    .registers 15
    .param p1, "m"    # [F
    .param p2, "angle"    # F
    .param p3, "aspect"    # F
    .param p4, "near"    # F
    .param p5, "far"    # F

    .prologue
    const/4 v8, 0x0

    .line 1643
    const-wide/high16 v2, 0x3fe0000000000000L    # 0.5

    const-wide v4, 0x400921fb54442d18L    # Math.PI

    float-to-double v6, p2

    sub-double/2addr v4, v6

    mul-double/2addr v2, v4

    invoke-static {v2, v3}, Ljava/lang/Math;->tan(D)D

    move-result-wide v2

    double-to-float v0, v2

    .line 1644
    .local v0, "f":F
    sub-float v1, p4, p5

    .line 1645
    .local v1, "range":F
    const/4 v2, 0x0

    div-float v3, v0, p3

    aput v3, p1, v2

    .line 1646
    const/4 v2, 0x1

    aput v8, p1, v2

    .line 1647
    const/4 v2, 0x2

    aput v8, p1, v2

    .line 1648
    const/4 v2, 0x3

    aput v8, p1, v2

    .line 1649
    const/4 v2, 0x4

    aput v8, p1, v2

    .line 1650
    const/4 v2, 0x5

    aput v0, p1, v2

    .line 1651
    const/4 v2, 0x6

    aput v8, p1, v2

    .line 1652
    const/4 v2, 0x7

    aput v8, p1, v2

    .line 1653
    const/16 v2, 0x8

    aput v8, p1, v2

    .line 1654
    const/16 v2, 0x9

    aput v8, p1, v2

    .line 1655
    const/16 v2, 0xa

    div-float v3, p5, v1

    aput v3, p1, v2

    .line 1656
    const/16 v2, 0xb

    const/high16 v3, -0x40800000    # -1.0f

    aput v3, p1, v2

    .line 1657
    const/16 v2, 0xc

    aput v8, p1, v2

    .line 1658
    const/16 v2, 0xd

    aput v8, p1, v2

    .line 1659
    const/16 v2, 0xe

    mul-float v3, p4, p5

    div-float/2addr v3, v1

    aput v3, p1, v2

    .line 1660
    const/16 v2, 0xf

    aput v8, p1, v2

    .line 1661
    return-void
.end method

.method private playDragSound(I)V
    .registers 11
    .param p1, "soundId"    # I

    .prologue
    const/4 v4, 0x0

    const/high16 v2, 0x3f800000    # 1.0f

    .line 1372
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isSystemSoundChecked:Z

    if-eqz v0, :cond_22

    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    if-eqz v0, :cond_22

    .line 1373
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds:[I

    aget v1, v1, p1

    move v3, v2

    move v5, v4

    move v6, v2

    invoke-virtual/range {v0 .. v6}, Landroid/media/SoundPool;->play(IFFIIF)I

    move-result v8

    .line 1374
    .local v8, "streanID":I
    add-int/lit8 v8, v8, -0x1

    .line 1375
    new-instance v7, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$SoundPoolThread;

    invoke-direct {v7, p0, v8}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$SoundPoolThread;-><init>(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;I)V

    .line 1376
    .local v7, "soundpoolThread":Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$SoundPoolThread;
    invoke-virtual {v7}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$SoundPoolThread;->run()V

    .line 1378
    .end local v7    # "soundpoolThread":Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$SoundPoolThread;
    .end local v8    # "streanID":I
    :cond_22
    return-void
.end method

.method private playSound(I)V
    .registers 9
    .param p1, "soundId"    # I

    .prologue
    const/4 v4, 0x0

    const/high16 v2, 0x3f800000    # 1.0f

    .line 1365
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isSystemSoundChecked:Z

    if-eqz v0, :cond_17

    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    if-eqz v0, :cond_17

    .line 1366
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds:[I

    aget v1, v1, p1

    move v3, v2

    move v5, v4

    move v6, v2

    invoke-virtual/range {v0 .. v6}, Landroid/media/SoundPool;->play(IFFIIF)I

    .line 1368
    :cond_17
    return-void
.end method

.method private playSound_gravity(I)V
    .registers 9
    .param p1, "soundId"    # I

    .prologue
    const/4 v4, 0x0

    const/high16 v2, 0x3f800000    # 1.0f

    .line 1381
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isSystemSoundChecked:Z

    if-eqz v0, :cond_17

    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool_Gravity:Landroid/media/SoundPool;

    if-eqz v0, :cond_17

    .line 1382
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool_Gravity:Landroid/media/SoundPool;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds_gravity:[I

    aget v1, v1, p1

    move v3, v2

    move v5, v4

    move v6, v2

    invoke-virtual/range {v0 .. v6}, Landroid/media/SoundPool;->play(IFFIIF)I

    .line 1384
    :cond_17
    return-void
.end method

.method private recycleBitmap()V
    .registers 4

    .prologue
    const/4 v2, 0x0

    .line 1869
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "recycleBitmap"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1871
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapWater:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_13

    .line 1873
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapWater:Landroid/graphics/Bitmap;

    invoke-virtual {v0}, Landroid/graphics/Bitmap;->recycle()V

    .line 1874
    iput-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapWater:Landroid/graphics/Bitmap;

    .line 1877
    :cond_13
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_1e

    .line 1879
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    invoke-virtual {v0}, Landroid/graphics/Bitmap;->recycle()V

    .line 1880
    iput-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    .line 1883
    :cond_1e
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v0, v1, :cond_3a

    .line 1884
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapGravity:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_2f

    .line 1885
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapGravity:Landroid/graphics/Bitmap;

    invoke-virtual {v0}, Landroid/graphics/Bitmap;->recycle()V

    .line 1886
    iput-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapGravity:Landroid/graphics/Bitmap;

    .line 1888
    :cond_2f
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_3a

    .line 1889
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics:Landroid/graphics/Bitmap;

    invoke-virtual {v0}, Landroid/graphics/Bitmap;->recycle()V

    .line 1890
    iput-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics:Landroid/graphics/Bitmap;

    .line 1893
    :cond_3a
    return-void
.end method

.method private releaseCpuGpuMaxLock()V
    .registers 2

    .prologue
    .line 2780
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    if-eqz v0, :cond_8

    .line 2782
    const/4 v0, 0x0

    invoke-virtual {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->releaseBooster(I)V

    .line 2785
    :cond_8
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_10

    .line 2787
    const/4 v0, 0x1

    invoke-virtual {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->releaseBooster(I)V

    .line 2789
    :cond_10
    return-void
.end method

.method private removeDefaultRunnable()V
    .registers 3

    .prologue
    .line 2230
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDefaultRunnable:Ljava/lang/Runnable;

    if-eqz v0, :cond_15

    .line 2233
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "mDefaultRunnable isn\'t null, mParent.removeCallbacks(mDefaultRunnable)"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2235
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDefaultRunnable:Ljava/lang/Runnable;

    invoke-virtual {v0, v1}, Landroid/view/View;->removeCallbacks(Ljava/lang/Runnable;)Z

    .line 2236
    const/4 v0, 0x0

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDefaultRunnable:Ljava/lang/Runnable;

    .line 2238
    :cond_15
    return-void
.end method

.method private ripple(FFFZ)V
    .registers 13
    .param p1, "mx"    # F
    .param p2, "my"    # F
    .param p3, "intensity"    # F
    .param p4, "isTouchRipple"    # Z

    .prologue
    .line 1624
    if-eqz p4, :cond_8

    .line 1625
    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mPreviousRippleTime:J

    .line 1627
    :cond_8
    const-string v0, "CircleUnlockRippleRenderer"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "ripple(), mx = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v2, ", my = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, p2}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v2, ", intensity = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, p3}, Ljava/lang/StringBuilder;->append(F)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1629
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v0, Landroid/opengl/GLSurfaceView;

    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 1630
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocity:[F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    iget v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    move v5, p1

    move v6, p2

    move v7, p3

    invoke-static/range {v0 .. v7}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->ripple([FIIIIFFF)V

    .line 1632
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    if-nez v0, :cond_54

    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_5a

    .line 1633
    :cond_54
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    .line 1634
    :cond_5a
    return-void
.end method

.method private setBackground(Z)V
    .registers 16
    .param p1, "isLoadWaterBitmap"    # Z

    .prologue
    .line 1897
    const-string v10, "CircleUnlockRippleRenderer"

    const-string v11, "setBackground"

    invoke-static {v10, v11}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1901
    :try_start_7
    iget-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-static {v10}, Lcom/android/internal/policy/impl/keyguard/sec/LockscreenWallpaper;->isFlipboardWallpaper(Landroid/content/Context;)Z

    move-result v10

    if-eqz v10, :cond_5d

    .line 1902
    iget-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-static {v10}, Lcom/android/internal/policy/impl/keyguard/sec/FlipboardWallpaperWidget;->getWallpaperBitmap(Landroid/content/Context;)Landroid/graphics/Bitmap;

    move-result-object v10

    invoke-virtual {p0, v10}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setTexture(Landroid/graphics/Bitmap;)V

    .line 1903
    iget-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    invoke-virtual {v10}, Landroid/view/View;->getResources()Landroid/content/res/Resources;

    move-result-object v10

    sget v11, Lcom/codex/s4unlockfx/R$drawable;->s3_reflectionmap:I

    invoke-static {v10, v11}, Landroid/graphics/BitmapFactory;->decodeResource(Landroid/content/res/Resources;I)Landroid/graphics/Bitmap;

    move-result-object v10

    invoke-virtual {p0, v10}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setWaterTexture(Landroid/graphics/Bitmap;)V

    .line 1905
    sget-object v10, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v11, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v10, v11, :cond_58

    .line 1906
    iget-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    invoke-virtual {v10}, Landroid/view/View;->getResources()Landroid/content/res/Resources;

    move-result-object v10

    const v11, 0x1080125

    invoke-static {v10, v11}, Landroid/graphics/BitmapFactory;->decodeResource(Landroid/content/res/Resources;I)Landroid/graphics/Bitmap;

    move-result-object v10

    iget-object v11, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    invoke-virtual {v11}, Landroid/view/View;->getResources()Landroid/content/res/Resources;

    move-result-object v11

    const v12, 0x1080271

    invoke-static {v11, v12}, Landroid/graphics/BitmapFactory;->decodeResource(Landroid/content/res/Resources;I)Landroid/graphics/Bitmap;

    move-result-object v11

    iget-object v12, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    invoke-virtual {v12}, Landroid/view/View;->getResources()Landroid/content/res/Resources;

    move-result-object v12

    const v13, 0x1080272

    invoke-static {v12, v13}, Landroid/graphics/BitmapFactory;->decodeResource(Landroid/content/res/Resources;I)Landroid/graphics/Bitmap;

    move-result-object v12

    invoke-virtual {p0, v10, v11, v12}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setGravityTexture(Landroid/graphics/Bitmap;Landroid/graphics/Bitmap;Landroid/graphics/Bitmap;)V

    .line 1913
    :cond_58
    const/4 v10, 0x1

    invoke-direct {p0, v10}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->transferBitmapToJni(Z)V

    .line 2019
    :goto_5c
    return-void

    .line 1917
    :cond_5d
    iget-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v10}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v10

    const-string v11, "lockscreen_wallpaper_path"

    const/4 v12, 0x0

    invoke-static {v10, v11, v12}, Landroid/provider/Settings$System;->getStringForUser(Landroid/content/ContentResolver;Ljava/lang/String;I)Ljava/lang/String;

    move-result-object v10

    iput-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mWallpaperPath:Ljava/lang/String;

    .line 1920
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/MultiSimUtils;->isMultiSIMDevice()Z

    move-result v10

    if-eqz v10, :cond_94

    .line 1922
    iget-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-static {v10}, Lcom/android/internal/policy/impl/keyguard/sec/MultiSimUtils;->getCurrentWallpaper(Landroid/content/Context;)Ljava/lang/String;

    move-result-object v10

    iput-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mWallpaperPath:Ljava/lang/String;

    .line 1923
    const-string v10, "CircleUnlockRippleRenderer"

    new-instance v11, Ljava/lang/StringBuilder;

    invoke-direct {v11}, Ljava/lang/StringBuilder;-><init>()V

    const-string v12, "MultiSim Device wallpaperPath:"

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v11

    iget-object v12, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mWallpaperPath:Ljava/lang/String;

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v11

    invoke-virtual {v11}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v11

    invoke-static {v10, v11}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1926
    :cond_94
    iget-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mWallpaperPath:Ljava/lang/String;

    if-nez v10, :cond_9c

    .line 1927
    const-string v10, "/data/data/com.sec.android.gallery3d/lockscreen_wallpaper_ripple.jpg"

    iput-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mWallpaperPath:Ljava/lang/String;

    .line 1930
    :cond_9c
    const/4 v6, 0x0

    .line 1931
    .local v6, "inputStream":Ljava/io/InputStream;
    new-instance v9, Ljava/io/File;

    iget-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mWallpaperPath:Ljava/lang/String;

    invoke-direct {v9, v10}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 1933
    .local v9, "wallpaperFile":Ljava/io/File;
    const-string v10, "CircleUnlockRippleRenderer"

    new-instance v11, Ljava/lang/StringBuilder;

    invoke-direct {v11}, Ljava/lang/StringBuilder;-><init>()V

    const-string v12, "wallpaperPath:"

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v11

    iget-object v12, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mWallpaperPath:Ljava/lang/String;

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v11

    invoke-virtual {v11}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v11

    invoke-static {v10, v11}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1936
    new-instance v0, Ljava/io/File;

    const-string v10, "/data/system/enterprise/lso/lockscreen_wallpaper_ripple.jpg"

    invoke-direct {v0, v10}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 1937
    .local v0, "adminWallpaperFile":Ljava/io/File;
    invoke-virtual {v0}, Ljava/io/File;->exists()Z

    move-result v10

    if-eqz v10, :cond_16e

    invoke-virtual {v0}, Ljava/io/File;->canRead()Z

    move-result v10

    if-eqz v10, :cond_16e

    .line 1939
    const-string v10, "CircleUnlockRippleRenderer"

    const-string v11, "adminWallpaperFile wallpaperPath:/data/system/enterprise/lso/lockscreen_wallpaper_ripple.jpg"

    invoke-static {v10, v11}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1940
    new-instance v6, Ljava/io/FileInputStream;

    .end local v6    # "inputStream":Ljava/io/InputStream;
    invoke-direct {v6, v0}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V

    .line 1941
    .restart local v6    # "inputStream":Ljava/io/InputStream;
    const-string v10, "/data/system/enterprise/lso/lockscreen_wallpaper_ripple.jpg"

    iput-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mWallpaperPath:Ljava/lang/String;

    .line 1978
    :goto_e1
    new-instance v10, Landroid/graphics/drawable/BitmapDrawable;

    invoke-direct {v10, v6}, Landroid/graphics/drawable/BitmapDrawable;-><init>(Ljava/io/InputStream;)V

    invoke-virtual {v10}, Landroid/graphics/drawable/BitmapDrawable;->getBitmap()Landroid/graphics/Bitmap;

    move-result-object v8

    .line 1980
    .local v8, "pBitmap":Landroid/graphics/Bitmap;
    if-eqz v8, :cond_fa

    if-eqz v8, :cond_119

    invoke-virtual {v8}, Landroid/graphics/Bitmap;->getWidth()I

    move-result v10

    if-nez v10, :cond_119

    invoke-virtual {v8}, Landroid/graphics/Bitmap;->getHeight()I

    move-result v10

    if-nez v10, :cond_119

    .line 1982
    :cond_fa
    if-nez v8, :cond_1f8

    .line 1984
    const-string v10, "CircleUnlockRippleRenderer"

    const-string v11, "pBitmap is null"

    invoke-static {v10, v11}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1991
    :goto_103
    iget-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v10}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v10

    sget v11, Lcom/codex/s4unlockfx/R$drawable;->keyguard_default_wallpaper:I

    invoke-virtual {v10, v11}, Landroid/content/res/Resources;->openRawResource(I)Ljava/io/InputStream;

    move-result-object v6

    .line 1992
    new-instance v10, Landroid/graphics/drawable/BitmapDrawable;

    invoke-direct {v10, v6}, Landroid/graphics/drawable/BitmapDrawable;-><init>(Ljava/io/InputStream;)V

    invoke-virtual {v10}, Landroid/graphics/drawable/BitmapDrawable;->getBitmap()Landroid/graphics/Bitmap;

    move-result-object v8

    .line 1995
    :cond_119
    invoke-virtual {p0, v8}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setTexture(Landroid/graphics/Bitmap;)V

    .line 1997
    invoke-virtual {v6}, Ljava/io/InputStream;->close()V

    .line 1998
    if-eqz p1, :cond_169

    .line 2000
    const/4 v7, 0x0

    .line 2001
    .local v7, "istr_WaterTexture":Ljava/io/InputStream;
    iget-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v10}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v10

    sget v11, Lcom/codex/s4unlockfx/R$drawable;->s3_reflectionmap:I

    invoke-virtual {v10, v11}, Landroid/content/res/Resources;->openRawResource(I)Ljava/io/InputStream;

    move-result-object v7

    .line 2002
    invoke-static {v7}, Landroid/graphics/BitmapFactory;->decodeStream(Ljava/io/InputStream;)Landroid/graphics/Bitmap;

    move-result-object v10

    invoke-virtual {p0, v10}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setWaterTexture(Landroid/graphics/Bitmap;)V

    .line 2003
    invoke-virtual {v7}, Ljava/io/InputStream;->close()V

    .line 2005
    sget-object v10, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v11, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v10, v11, :cond_169

    .line 2006
    iget-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    invoke-virtual {v10}, Landroid/view/View;->getResources()Landroid/content/res/Resources;

    move-result-object v10

    const v11, 0x1080125

    invoke-static {v10, v11}, Landroid/graphics/BitmapFactory;->decodeResource(Landroid/content/res/Resources;I)Landroid/graphics/Bitmap;

    move-result-object v10

    iget-object v11, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    invoke-virtual {v11}, Landroid/view/View;->getResources()Landroid/content/res/Resources;

    move-result-object v11

    const v12, 0x1080271

    invoke-static {v11, v12}, Landroid/graphics/BitmapFactory;->decodeResource(Landroid/content/res/Resources;I)Landroid/graphics/Bitmap;

    move-result-object v11

    iget-object v12, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    invoke-virtual {v12}, Landroid/view/View;->getResources()Landroid/content/res/Resources;

    move-result-object v12

    const v13, 0x1080272

    invoke-static {v12, v13}, Landroid/graphics/BitmapFactory;->decodeResource(Landroid/content/res/Resources;I)Landroid/graphics/Bitmap;

    move-result-object v12

    invoke-virtual {p0, v10, v11, v12}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setGravityTexture(Landroid/graphics/Bitmap;Landroid/graphics/Bitmap;Landroid/graphics/Bitmap;)V
    :try_end_169
    .catch Ljava/lang/Exception; {:try_start_7 .. :try_end_169} :catch_224

    .line 2018
    .end local v0    # "adminWallpaperFile":Ljava/io/File;
    .end local v6    # "inputStream":Ljava/io/InputStream;
    .end local v7    # "istr_WaterTexture":Ljava/io/InputStream;
    .end local v8    # "pBitmap":Landroid/graphics/Bitmap;
    .end local v9    # "wallpaperFile":Ljava/io/File;
    :cond_169
    :goto_169
    invoke-direct {p0, p1}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->transferBitmapToJni(Z)V

    goto/16 :goto_5c

    .line 1944
    .restart local v0    # "adminWallpaperFile":Ljava/io/File;
    .restart local v6    # "inputStream":Ljava/io/InputStream;
    .restart local v9    # "wallpaperFile":Ljava/io/File;
    :cond_16e
    :try_start_16e
    invoke-virtual {v9}, Ljava/io/File;->exists()Z

    move-result v10

    if-eqz v10, :cond_181

    invoke-virtual {v9}, Ljava/io/File;->canRead()Z

    move-result v10

    if-eqz v10, :cond_181

    .line 1945
    new-instance v6, Ljava/io/FileInputStream;

    .end local v6    # "inputStream":Ljava/io/InputStream;
    invoke-direct {v6, v9}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V

    .restart local v6    # "inputStream":Ljava/io/InputStream;
    goto/16 :goto_e1

    .line 1949
    :cond_181
    new-instance v1, Ljava/io/File;

    const-string v10, "/system/wallpaper/lockscreen_default_wallpaper.jpg"

    invoke-direct {v1, v10}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 1950
    .local v1, "defaultWallpaperFile":Ljava/io/File;
    new-instance v2, Ljava/io/File;

    const-string v10, "/system/csc_contents/lockscreen_default_wallpaper.jpg"

    invoke-direct {v2, v10}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 1951
    .local v2, "defaultWallpaperFileMultiCSC":Ljava/io/File;
    new-instance v4, Ljava/io/File;

    const-string v10, "/system/wallpaper/lockscreen_default_wallpaper.png"

    invoke-direct {v4, v10}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 1952
    .local v4, "defaultWallpaperFilePng":Ljava/io/File;
    new-instance v3, Ljava/io/File;

    const-string v10, "/system/csc_contents/lockscreen_default_wallpaper.png"

    invoke-direct {v3, v10}, Ljava/io/File;-><init>(Ljava/lang/String;)V

    .line 1954
    .local v3, "defaultWallpaperFileMultiCSCPng":Ljava/io/File;
    invoke-virtual {v3}, Ljava/io/File;->exists()Z

    move-result v10

    if-eqz v10, :cond_1b0

    invoke-virtual {v3}, Ljava/io/File;->canRead()Z

    move-result v10

    if-eqz v10, :cond_1b0

    .line 1956
    new-instance v6, Ljava/io/FileInputStream;

    .end local v6    # "inputStream":Ljava/io/InputStream;
    invoke-direct {v6, v3}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V

    .restart local v6    # "inputStream":Ljava/io/InputStream;
    goto/16 :goto_e1

    .line 1958
    :cond_1b0
    invoke-virtual {v2}, Ljava/io/File;->exists()Z

    move-result v10

    if-eqz v10, :cond_1c3

    invoke-virtual {v2}, Ljava/io/File;->canRead()Z

    move-result v10

    if-eqz v10, :cond_1c3

    .line 1960
    new-instance v6, Ljava/io/FileInputStream;

    .end local v6    # "inputStream":Ljava/io/InputStream;
    invoke-direct {v6, v2}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V

    .restart local v6    # "inputStream":Ljava/io/InputStream;
    goto/16 :goto_e1

    .line 1962
    :cond_1c3
    invoke-virtual {v4}, Ljava/io/File;->exists()Z

    move-result v10

    if-eqz v10, :cond_1d6

    invoke-virtual {v4}, Ljava/io/File;->canRead()Z

    move-result v10

    if-eqz v10, :cond_1d6

    .line 1964
    new-instance v6, Ljava/io/FileInputStream;

    .end local v6    # "inputStream":Ljava/io/InputStream;
    invoke-direct {v6, v4}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V

    .restart local v6    # "inputStream":Ljava/io/InputStream;
    goto/16 :goto_e1

    .line 1966
    :cond_1d6
    invoke-virtual {v1}, Ljava/io/File;->exists()Z

    move-result v10

    if-eqz v10, :cond_1e9

    invoke-virtual {v1}, Ljava/io/File;->canRead()Z

    move-result v10

    if-eqz v10, :cond_1e9

    .line 1968
    new-instance v6, Ljava/io/FileInputStream;

    .end local v6    # "inputStream":Ljava/io/InputStream;
    invoke-direct {v6, v1}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V

    .restart local v6    # "inputStream":Ljava/io/InputStream;
    goto/16 :goto_e1

    .line 1972
    :cond_1e9
    iget-object v10, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v10}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v10

    sget v11, Lcom/codex/s4unlockfx/R$drawable;->keyguard_default_wallpaper:I

    invoke-virtual {v10, v11}, Landroid/content/res/Resources;->openRawResource(I)Ljava/io/InputStream;

    move-result-object v6

    goto/16 :goto_e1

    .line 1988
    .end local v1    # "defaultWallpaperFile":Ljava/io/File;
    .end local v2    # "defaultWallpaperFileMultiCSC":Ljava/io/File;
    .end local v3    # "defaultWallpaperFileMultiCSCPng":Ljava/io/File;
    .end local v4    # "defaultWallpaperFilePng":Ljava/io/File;
    .restart local v8    # "pBitmap":Landroid/graphics/Bitmap;
    :cond_1f8
    const-string v10, "CircleUnlockRippleRenderer"

    new-instance v11, Ljava/lang/StringBuilder;

    invoke-direct {v11}, Ljava/lang/StringBuilder;-><init>()V

    const-string v12, "pBitmap.width = "

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v11

    invoke-virtual {v8}, Landroid/graphics/Bitmap;->getWidth()I

    move-result v12

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v11

    const-string v12, ", pBitmap.height = "

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v11

    invoke-virtual {v8}, Landroid/graphics/Bitmap;->getHeight()I

    move-result v12

    invoke-virtual {v11, v12}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v11

    invoke-virtual {v11}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v11

    invoke-static {v10, v11}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_222
    .catch Ljava/lang/Exception; {:try_start_16e .. :try_end_222} :catch_224

    goto/16 :goto_103

    .line 2012
    .end local v0    # "adminWallpaperFile":Ljava/io/File;
    .end local v6    # "inputStream":Ljava/io/InputStream;
    .end local v8    # "pBitmap":Landroid/graphics/Bitmap;
    .end local v9    # "wallpaperFile":Ljava/io/File;
    :catch_224
    move-exception v5

    .line 2014
    .local v5, "e":Ljava/lang/Exception;
    invoke-virtual {v5}, Ljava/lang/Throwable;->printStackTrace()V

    goto/16 :goto_169
.end method

.method private setFalseDefaultEffectFlag()V
    .registers 2

    .prologue
    const/4 v0, 0x0

    .line 2243
    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isFirstTouched:Z

    .line 2244
    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->calledScreenTurnedOn:Z

    .line 2245
    return-void
.end method

.method private setHoverEnable(Z)V
    .registers 4
    .param p1, "isEnable"    # Z

    .prologue
    .line 1297
    if-eqz p1, :cond_19

    .line 1299
    const-string v0, "Fresnel"

    const-string v1, "setHoverEnable is true"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1300
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_FRESENL_MIN:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mFresnelRatio:F

    .line 1301
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_SPECULAR_RATIO_MIN:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSpecularRatio:F

    .line 1302
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_EXPONENT_RATIO_MIN:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mExponentRatio:F

    .line 1303
    const/4 v0, 0x1

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mHoverEnabled:Z

    .line 1313
    :goto_18
    return-void

    .line 1307
    :cond_19
    const-string v0, "Fresnel"

    const-string v1, "setHoverEnable is false"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1308
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_FRESENL:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mFresnelRatio:F

    .line 1309
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_SPECULAR:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSpecularRatio:F

    .line 1310
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_EXPONENT:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mExponentRatio:F

    .line 1311
    const/4 v0, 0x0

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mHoverEnabled:Z

    goto :goto_18
.end method

.method private setModeleConfiguration()V
    .registers 8

    .prologue
    const/16 v6, 0x15

    const/16 v5, 0x68

    const/4 v4, 0x3

    const/16 v3, 0x32

    const/4 v2, 0x0

    .line 548
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    const/16 v1, 0x2d0

    if-ne v0, v1, :cond_14

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    const/16 v1, 0x500

    if-eq v0, v1, :cond_20

    :cond_14
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    const/16 v1, 0x500

    if-ne v0, v1, :cond_7a

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    const/16 v1, 0x2d0

    if-ne v0, v1, :cond_7a

    .line 550
    :cond_20
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    .line 551
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    .line 552
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    .line 553
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    .line 554
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    add-int/lit8 v0, v0, -0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    .line 555
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    add-int/lit8 v0, v0, -0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    .line 556
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    mul-int/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->VCOUNT:I

    .line 559
    const v0, 0x3eb33333    # 0.35f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForLandscape:F

    .line 560
    const/high16 v0, 0x3f000000    # 0.5f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForPortrait:F

    .line 564
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForPortrait:F

    .line 565
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForLandscape:F

    .line 568
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForPortrait:F

    .line 569
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForLandscape:F

    .line 572
    const v0, -0x3dd3cccd    # -43.05f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForPortrait:F

    .line 573
    const v0, -0x3e41999a    # -23.8f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForLandscape:F

    .line 576
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForLandscape:I

    .line 577
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForLandscape:I

    .line 578
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForPortrait:I

    .line 579
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForPortrait:I

    .line 582
    const/high16 v0, 0x42340000    # 45.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForLandscape:F

    .line 583
    const/high16 v0, 0x41c80000    # 25.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForLandscape:F

    .line 584
    const/high16 v0, 0x41f00000    # 30.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForPortrait:F

    .line 585
    const/high16 v0, 0x42380000    # 46.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForPortrait:F

    .line 586
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustPortrait:F

    .line 587
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustLandscape:F

    .line 809
    :goto_72
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    mul-int/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->max:I

    .line 810
    return-void

    .line 590
    :cond_7a
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    const/16 v1, 0x21c

    if-ne v0, v1, :cond_86

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    const/16 v1, 0x3c0

    if-eq v0, v1, :cond_92

    :cond_86
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    const/16 v1, 0x3c0

    if-ne v0, v1, :cond_e5

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    const/16 v1, 0x21c

    if-ne v0, v1, :cond_e5

    .line 593
    :cond_92
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    .line 594
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    .line 596
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    .line 597
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    .line 598
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    add-int/lit8 v0, v0, -0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    .line 599
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    add-int/lit8 v0, v0, -0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    .line 600
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    mul-int/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->VCOUNT:I

    .line 603
    const v0, 0x3eb33333    # 0.35f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForLandscape:F

    .line 604
    const/high16 v0, 0x3f000000    # 0.5f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForPortrait:F

    .line 608
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForPortrait:F

    .line 609
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForLandscape:F

    .line 612
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForPortrait:F

    .line 613
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForLandscape:F

    .line 616
    const v0, -0x3dd3cccd    # -43.05f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForPortrait:F

    .line 617
    const v0, -0x3e41999a    # -23.8f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForLandscape:F

    .line 620
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForLandscape:I

    .line 621
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForLandscape:I

    .line 622
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForPortrait:I

    .line 623
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForPortrait:I

    .line 626
    const/high16 v0, 0x42340000    # 45.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForLandscape:F

    .line 627
    const/high16 v0, 0x41c80000    # 25.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForLandscape:F

    .line 628
    const/high16 v0, 0x41f00000    # 30.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForPortrait:F

    .line 629
    const/high16 v0, 0x42380000    # 46.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForPortrait:F

    .line 630
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustPortrait:F

    .line 631
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustLandscape:F

    goto :goto_72

    .line 634
    :cond_e5
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    const/16 v1, 0x500

    if-ne v0, v1, :cond_f1

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    const/16 v1, 0x320

    if-eq v0, v1, :cond_fd

    :cond_f1
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    const/16 v1, 0x320

    if-ne v0, v1, :cond_153

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    const/16 v1, 0x500

    if-ne v0, v1, :cond_153

    .line 637
    :cond_fd
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    .line 638
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    .line 639
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    .line 640
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    .line 641
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    add-int/lit8 v0, v0, -0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    .line 642
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    add-int/lit8 v0, v0, -0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    .line 643
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    mul-int/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->VCOUNT:I

    .line 646
    const v0, 0x3eb33333    # 0.35f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForLandscape:F

    .line 647
    const/high16 v0, 0x3f000000    # 0.5f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForPortrait:F

    .line 651
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForPortrait:F

    .line 652
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForLandscape:F

    .line 655
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForPortrait:F

    .line 656
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForLandscape:F

    .line 659
    const v0, -0x3dd3cccd    # -43.05f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForPortrait:F

    .line 660
    const v0, -0x3e388937    # -24.933f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForLandscape:F

    .line 663
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForLandscape:I

    .line 664
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForLandscape:I

    .line 665
    const/16 v0, 0x13

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForPortrait:I

    .line 666
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForPortrait:I

    .line 669
    const/high16 v0, 0x42400000    # 48.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForLandscape:F

    .line 670
    const/high16 v0, 0x41d80000    # 27.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForLandscape:F

    .line 671
    const/high16 v0, 0x41f00000    # 30.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForPortrait:F

    .line 672
    const/high16 v0, 0x42380000    # 46.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForPortrait:F

    .line 673
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustPortrait:F

    .line 674
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustLandscape:F

    goto/16 :goto_72

    .line 678
    :cond_153
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    const/16 v1, 0x1e0

    if-ne v0, v1, :cond_15f

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    const/16 v1, 0x320

    if-eq v0, v1, :cond_16b

    :cond_15f
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    const/16 v1, 0x320

    if-ne v0, v1, :cond_1c5

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    const/16 v1, 0x1e0

    if-ne v0, v1, :cond_1c5

    .line 681
    :cond_16b
    const/16 v0, 0x4a

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    .line 682
    const/16 v0, 0x4a

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    .line 683
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    .line 684
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    .line 685
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    add-int/lit8 v0, v0, -0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    .line 686
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    add-int/lit8 v0, v0, -0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    .line 687
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    mul-int/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->VCOUNT:I

    .line 690
    const v0, 0x3eb33333    # 0.35f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForLandscape:F

    .line 691
    const/high16 v0, 0x3f000000    # 0.5f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForPortrait:F

    .line 695
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForPortrait:F

    .line 696
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForLandscape:F

    .line 699
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForPortrait:F

    .line 700
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForLandscape:F

    .line 703
    const v0, -0x3dd3cccd    # -43.05f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForPortrait:F

    .line 704
    const v0, -0x3e41999a    # -23.8f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForLandscape:F

    .line 707
    const/4 v0, 0x2

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForLandscape:I

    .line 708
    const/16 v0, 0xe

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForLandscape:I

    .line 709
    const/16 v0, 0xe

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForPortrait:I

    .line 710
    const/4 v0, 0x2

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForPortrait:I

    .line 713
    const/high16 v0, 0x42340000    # 45.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForLandscape:F

    .line 714
    const/high16 v0, 0x41c80000    # 25.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForLandscape:F

    .line 715
    const/high16 v0, 0x41e00000    # 28.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForPortrait:F

    .line 716
    const/high16 v0, 0x42340000    # 45.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForPortrait:F

    goto/16 :goto_72

    .line 718
    :cond_1c5
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    const/16 v1, 0xa00

    if-ne v0, v1, :cond_1d1

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    const/16 v1, 0x640

    if-eq v0, v1, :cond_1dd

    :cond_1d1
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    const/16 v1, 0x640

    if-ne v0, v1, :cond_255

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    const/16 v1, 0xa00

    if-ne v0, v1, :cond_255

    .line 720
    :cond_1dd
    const/16 v0, 0x4a

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    .line 721
    const/16 v0, 0x4a

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    .line 722
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    .line 723
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    .line 724
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    add-int/lit8 v0, v0, -0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    .line 725
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    add-int/lit8 v0, v0, -0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    .line 726
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    mul-int/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->VCOUNT:I

    .line 729
    const v0, 0x3e4ccccd    # 0.2f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForLandscape:F

    .line 730
    const v0, 0x3eb33333    # 0.35f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForPortrait:F

    .line 734
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForPortrait:F

    .line 735
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForLandscape:F

    .line 738
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForPortrait:F

    .line 739
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForLandscape:F

    .line 742
    const v0, -0x3dd3cccd    # -43.05f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForPortrait:F

    .line 743
    const v0, -0x3e2ccccd    # -26.4f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForLandscape:F

    .line 746
    const/4 v0, 0x2

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForLandscape:I

    .line 747
    const/16 v0, 0xe

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForLandscape:I

    .line 748
    const/16 v0, 0xe

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForPortrait:I

    .line 749
    const/4 v0, 0x2

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForPortrait:I

    .line 752
    const-wide v0, 0x406f400000000000L    # 250.0

    iput-wide v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rippleDragThreshold:D

    .line 755
    const/high16 v0, 0x42340000    # 45.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForLandscape:F

    .line 756
    const/high16 v0, 0x41c80000    # 25.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForLandscape:F

    .line 757
    const/high16 v0, 0x41f00000    # 30.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForPortrait:F

    .line 758
    const/high16 v0, 0x42380000    # 46.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForPortrait:F

    .line 759
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustPortrait:F

    .line 760
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustLandscape:F

    .line 762
    const v0, 0x3e4ccccd    # 0.2f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->reflectionRatio:F

    .line 763
    const v0, 0x3dcccccd    # 0.1f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_FRESENL:F

    .line 764
    const/high16 v0, 0x3fc00000    # 1.5f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_SPECULAR:F

    .line 765
    const/high16 v0, 0x42200000    # 40.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TOUCH_EXPONENT:F

    goto/16 :goto_72

    .line 769
    :cond_255
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    .line 770
    iput v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    .line 771
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    .line 772
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    .line 773
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    add-int/lit8 v0, v0, -0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    .line 774
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    add-int/lit8 v0, v0, -0x4

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    .line 775
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_WIDTH:I

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->SURFACE_DETAILS_HEIGHT:I

    mul-int/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->VCOUNT:I

    .line 778
    const v0, 0x3eb33333    # 0.35f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForLandscape:F

    .line 779
    const/high16 v0, 0x3f000000    # 0.5f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForPortrait:F

    .line 783
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForPortrait:F

    .line 784
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForLandscape:F

    .line 787
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForPortrait:F

    .line 788
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForLandscape:F

    .line 791
    const v0, -0x3dd3cccd    # -43.05f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForPortrait:F

    .line 792
    const v0, -0x3e41999a    # -23.8f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForLandscape:F

    .line 795
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForLandscape:I

    .line 796
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForLandscape:I

    .line 797
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanXForPortrait:I

    .line 798
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->spanYForPortrait:I

    .line 801
    const/high16 v0, 0x42340000    # 45.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForLandscape:F

    .line 802
    const/high16 v0, 0x41c80000    # 25.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForLandscape:F

    .line 803
    const/high16 v0, 0x41f00000    # 30.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForPortrait:F

    .line 804
    const/high16 v0, 0x42380000    # 46.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForPortrait:F

    .line 805
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustPortrait:F

    .line 806
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustLandscape:F

    goto/16 :goto_72
.end method

.method private setRenderModeDirty()V
    .registers 3

    .prologue
    .line 1607
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v0, Landroid/opengl/GLSurfaceView;

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 1608
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "RENDERMODE_WHEN_DIRTY!!!"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1610
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    if-nez v0, :cond_17

    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_1d

    .line 1611
    :cond_17
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    .line 1612
    :cond_1d
    return-void
.end method

.method private setRippleVersion()V
    .registers 5

    .prologue
    .line 1811
    const-string v1, "CircleUnlockRippleRenderer"

    const-string v2, "setRippleVersion"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1812
    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v1}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v1

    const-string v2, "com.sec.feature.spen_usp"

    invoke-virtual {v1, v2}, Landroid/content/pm/PackageManager;->hasSystemFeature(Ljava/lang/String;)Z

    move-result v1

    if-eqz v1, :cond_3e

    .line 1815
    :try_start_15
    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v1}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v1

    const-string v2, "pen_hovering_ink_effect"

    const/4 v3, 0x0

    invoke-static {v1, v2, v3}, Landroid/provider/Settings$System;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I

    move-result v1

    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    .line 1816
    const-string v1, "CircleUnlockRippleRenderer"

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "mInkEffectColor = "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_3e
    .catch Ljava/lang/Exception; {:try_start_15 .. :try_end_3e} :catch_52

    .line 1822
    :cond_3e
    :goto_3e
    iget-boolean v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isInkEnable:Z

    if-eqz v1, :cond_57

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    if-eqz v1, :cond_57

    .line 1824
    const-string v1, "CircleUnlockRippleRenderer"

    const-string v2, "Def.MODE = ModeType.RIPPLE_LIGHT_WITH_INK"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1825
    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sput-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    .line 1837
    :goto_51
    return-void

    .line 1817
    :catch_52
    move-exception v0

    .line 1818
    .local v0, "e":Ljava/lang/Exception;
    invoke-virtual {v0}, Ljava/lang/Throwable;->printStackTrace()V

    goto :goto_3e

    .line 1827
    .end local v0    # "e":Ljava/lang/Exception;
    :cond_57
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/KeyguardProperties;->isArcMotionEnabled()Z

    move-result v1

    if-eqz v1, :cond_69

    .line 1829
    const-string v1, "CircleUnlockRippleRenderer"

    const-string v2, "ModeType.RIPPLE_GRAVITY_LIGHT"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1830
    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sput-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    goto :goto_51

    .line 1834
    :cond_69
    const-string v1, "CircleUnlockRippleRenderer"

    const-string v2, "ModeType.RIPPLE_LIGHT"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1835
    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sput-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    goto :goto_51
.end method

.method private setSound()V
    .registers 7

    .prologue
    const/4 v5, 0x0

    const/4 v4, 0x1

    .line 2029
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    if-nez v0, :cond_4a

    .line 2031
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "show mSoundPool is null"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2033
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-static {v0}, Lcom/android/internal/policy/impl/keyguard/KeyguardUpdateMonitor;->getInstance(Landroid/content/Context;)Lcom/android/internal/policy/impl/keyguard/KeyguardUpdateMonitor;

    move-result-object v0

    invoke-virtual {v0}, Lcom/android/internal/policy/impl/keyguard/KeyguardUpdateMonitor;->hasBootCompleted()Z

    move-result v0

    if-eqz v0, :cond_4a

    .line 2035
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "KeyguardUpdateMonitor hasBootCompleted"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2036
    new-instance v0, Landroid/media/SoundPool;

    const/16 v1, 0xa

    invoke-direct {v0, v1, v4, v5}, Landroid/media/SoundPool;-><init>(III)V

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    .line 2037
    const/4 v0, 0x2

    new-array v0, v0, [I

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds:[I

    .line 2038
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds:[I

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRDownId:I

    invoke-virtual {v1, v2, v3, v4}, Landroid/media/SoundPool;->load(Landroid/content/Context;II)I

    move-result v1

    aput v1, v0, v5

    .line 2039
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds:[I

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRUpId:I

    invoke-virtual {v1, v2, v3, v4}, Landroid/media/SoundPool;->load(Landroid/content/Context;II)I

    move-result v1

    aput v1, v0, v4

    .line 2042
    :cond_4a
    return-void
.end method

.method private setSound_gravity()V
    .registers 7

    .prologue
    const/4 v5, 0x0

    const/4 v4, 0x1

    .line 2046
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool_Gravity:Landroid/media/SoundPool;

    if-nez v0, :cond_3c

    .line 2048
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "show mSoundPool_Gravity is null"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2050
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-static {v0}, Lcom/android/internal/policy/impl/keyguard/KeyguardUpdateMonitor;->getInstance(Landroid/content/Context;)Lcom/android/internal/policy/impl/keyguard/KeyguardUpdateMonitor;

    move-result-object v0

    invoke-virtual {v0}, Lcom/android/internal/policy/impl/keyguard/KeyguardUpdateMonitor;->hasBootCompleted()Z

    move-result v0

    if-eqz v0, :cond_3c

    .line 2052
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "KeyguardUpdateMonitor hasBootCompleted"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2053
    new-instance v0, Landroid/media/SoundPool;

    const/16 v1, 0xa

    invoke-direct {v0, v1, v4, v5}, Landroid/media/SoundPool;-><init>(III)V

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool_Gravity:Landroid/media/SoundPool;

    .line 2054
    new-array v0, v4, [I

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds_gravity:[I

    .line 2055
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds_gravity:[I

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool_Gravity:Landroid/media/SoundPool;

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    sget v3, Lcom/codex/s4unlockfx/R$raw;->s3_gravity_effect:I

    invoke-virtual {v1, v2, v3, v4}, Landroid/media/SoundPool;->load(Landroid/content/Context;II)I

    move-result v1

    aput v1, v0, v5

    .line 2058
    :cond_3c
    return-void
.end method

.method private transferBitmapToJni(Z)V
    .registers 5
    .param p1, "isLoadWaterBitmap"    # Z

    .prologue
    .line 1794
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "transferBitmapToJni"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1795
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "transferBGBitmap"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1796
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    invoke-static {v0}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->transferBGBitmap(Landroid/graphics/Bitmap;)V

    .line 1798
    if-eqz p1, :cond_30

    .line 1799
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "transferWaterBitmap"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1800
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapWater:Landroid/graphics/Bitmap;

    invoke-static {v0}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->transferWaterBitmap(Landroid/graphics/Bitmap;)V

    .line 1802
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v0, v1, :cond_30

    .line 1803
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapGravity:Landroid/graphics/Bitmap;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics:Landroid/graphics/Bitmap;

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics2:Landroid/graphics/Bitmap;

    invoke-static {v0, v1, v2}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->transferGravityBitmap(Landroid/graphics/Bitmap;Landroid/graphics/Bitmap;Landroid/graphics/Bitmap;)V

    .line 1807
    :cond_30
    return-void
.end method


# virtual methods
.method public EffectDisable()V
    .registers 4

    .prologue
    const/4 v2, 0x1

    const/4 v1, -0x1

    .line 2649
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CurrentState:I

    packed-switch v0, :pswitch_data_22

    .line 2665
    :goto_7
    return-void

    .line 2651
    :pswitch_8
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v0, Landroid/opengl/GLSurfaceView;

    invoke-virtual {v0, v2}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 2652
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->unbindLeftDirectionEffect()V

    .line 2654
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CurrentState:I

    goto :goto_7

    .line 2658
    :pswitch_15
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v0, Landroid/opengl/GLSurfaceView;

    invoke-virtual {v0, v2}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 2659
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->unbindRightDirectionEffect()V

    .line 2661
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CurrentState:I

    goto :goto_7

    .line 2649
    :pswitch_data_22
    .packed-switch 0x0
        :pswitch_8
        :pswitch_15
    .end packed-switch
.end method

.method public EffectEnable(I)V
    .registers 6
    .param p1, "effectType"    # I

    .prologue
    const/4 v3, 0x0

    const/high16 v2, 0x3f800000    # 1.0f

    const/4 v1, 0x1

    .line 2624
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setSound_gravity()V

    .line 2625
    invoke-direct {p0, v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->playSound_gravity(I)V

    .line 2626
    packed-switch p1, :pswitch_data_2c

    .line 2645
    :goto_d
    return-void

    .line 2628
    :pswitch_e
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v0, Landroid/opengl/GLSurfaceView;

    invoke-virtual {v0, v1}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 2629
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bindLeftDirectionEffect()V

    .line 2630
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CurrentState:I

    .line 2631
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveReductionRate:F

    goto :goto_d

    .line 2637
    :pswitch_1d
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v0, Landroid/opengl/GLSurfaceView;

    invoke-virtual {v0, v1}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 2638
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bindRightDirectionEffect()V

    .line 2639
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CurrentState:I

    .line 2640
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveReductionRate:F

    goto :goto_d

    .line 2626
    :pswitch_data_2c
    .packed-switch 0x0
        :pswitch_e
        :pswitch_1d
    .end packed-switch
.end method

.method public alphaAnimation()V
    .registers 2

    .prologue
    .line 1664
    const/4 v0, 0x0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->reflectionRatio:F

    .line 1665
    return-void
.end method

.method public bindLeftDirectionEffect()V
    .registers 6

    .prologue
    const/4 v4, 0x1

    const/4 v3, 0x0

    const/4 v2, 0x0

    const/high16 v1, 0x3f800000    # 1.0f

    .line 2453
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v0, Landroid/opengl/GLSurfaceView;

    invoke-virtual {v0, v4}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 2455
    const/4 v0, 0x2

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    .line 2457
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    .line 2458
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDrawEffectFrameCnt:I

    .line 2459
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    .line 2462
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    .line 2464
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TexMoveU:F

    .line 2465
    iput-boolean v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bGravityDirection:Z

    .line 2466
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    .line 2467
    const v0, 0x3ea8f5c3    # 0.33f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 2468
    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    .line 2470
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->onMakeLeftDirectionStartRipple()V

    .line 2471
    return-void
.end method

.method public bindRightDirectionEffect()V
    .registers 6

    .prologue
    const/4 v4, 0x0

    const/4 v3, 0x0

    const/4 v2, 0x1

    const/high16 v1, 0x3f800000    # 1.0f

    .line 2337
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v0, Landroid/opengl/GLSurfaceView;

    invoke-virtual {v0, v2}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 2339
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    .line 2341
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    .line 2342
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDrawEffectFrameCnt:I

    .line 2344
    iput-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    .line 2346
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TexMoveU:F

    .line 2347
    iput-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bGravityDirection:Z

    .line 2348
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    .line 2349
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    .line 2350
    const v0, 0x3ea8f5c3    # 0.33f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 2351
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    .line 2353
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->onMakeRightDirectionStartRipple()V

    .line 2354
    return-void
.end method

.method public cleanUp()V
    .registers 6

    .prologue
    const/4 v4, 0x1

    const/4 v2, 0x0

    const/4 v3, 0x0

    .line 2108
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "cleanUp"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2110
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    if-eqz v0, :cond_17

    .line 2111
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    invoke-virtual {v0}, Landroid/media/SoundPool;->release()V

    .line 2112
    iput-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    .line 2113
    iput-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds:[I

    .line 2116
    :cond_17
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool_Gravity:Landroid/media/SoundPool;

    if-eqz v0, :cond_24

    .line 2117
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool_Gravity:Landroid/media/SoundPool;

    invoke-virtual {v0}, Landroid/media/SoundPool;->release()V

    .line 2118
    iput-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool_Gravity:Landroid/media/SoundPool;

    .line 2119
    iput-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds_gravity:[I

    .line 2123
    :cond_24
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->removeDefaultRunnable()V

    .line 2124
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setFalseDefaultEffectFlag()V

    .line 2125
    iput-boolean v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isTouched:Z

    .line 2126
    iput-boolean v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isShowCalled:Z

    .line 2127
    iput-boolean v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isScreenTurnedOn:Z

    .line 2128
    iput-boolean v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChanged:Z

    .line 2130
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-eq v0, v1, :cond_3e

    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v0, v1, :cond_56

    .line 2132
    :cond_3e
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isMakedASpenToucdUp:Z

    if-nez v0, :cond_56

    .line 2134
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "Spen onTouch(ACTION UP) , because touch up wasn\'t maked by uper layer until cleanUp "

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2135
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    float-to-int v0, v0

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseY:F

    float-to-int v1, v1

    const/high16 v2, 0x3f800000    # 1.0f

    invoke-static {v0, v1, v4, v2}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onTouch(IIIF)V

    .line 2136
    iput-boolean v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isMakedASpenToucdUp:Z

    .line 2140
    :cond_56
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    if-nez v0, :cond_5e

    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_63

    .line 2141
    :cond_5e
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    invoke-virtual {v0, v3}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    .line 2144
    :cond_63
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    new-instance v1, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$3;

    invoke-direct {v1, p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$3;-><init>(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)V

    const-wide/16 v2, 0x12c

    invoke-virtual {v0, v1, v2, v3}, Landroid/view/View;->postDelayed(Ljava/lang/Runnable;J)Z

    .line 2156
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v0, v1, :cond_7a

    .line 2157
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSContextManager:Landroid/hardware/scontext/SContextManager;

    invoke-virtual {v0, p0}, Landroid/hardware/scontext/SContextManager;->unregisterListener(Landroid/hardware/scontext/SContextListener;)V

    .line 2159
    :cond_7a
    return-void
.end method

.method public clearAllEffect()V
    .registers 3

    .prologue
    .line 1777
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->clearRipple()V

    .line 1779
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-eq v0, v1, :cond_f

    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v0, v1, :cond_19

    .line 1781
    :cond_f
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "clearInkValue"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1782
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->clearInkValue()V

    .line 1784
    :cond_19
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v0, v1, :cond_2c

    .line 1786
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "clear gravity"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1787
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->clearGravityEffect()V

    .line 1788
    const/4 v0, -0x1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    .line 1790
    :cond_2c
    return-void
.end method

.method public clearGravityEffect()V
    .registers 3

    .prologue
    const/4 v1, 0x0

    .line 2722
    const/high16 v0, 0x3f800000    # 1.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TexMoveU:F

    .line 2723
    const/4 v0, -0x1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->CurrentState:I

    .line 2724
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    .line 2725
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 2726
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    .line 2727
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeMix:F

    .line 2728
    const v0, 0x3f70a3d7    # 0.94f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveReductionRate:F

    .line 2729
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heights:[F

    invoke-static {v0, v1}, Ljava/util/Arrays;->fill([FF)V

    .line 2730
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocity:[F

    invoke-static {v0, v1}, Ljava/util/Arrays;->fill([FF)V

    .line 2731
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    invoke-static {v0, v1}, Ljava/util/Arrays;->fill([FF)V

    .line 2732
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    invoke-static {v0, v1}, Ljava/util/Arrays;->fill([FF)V

    .line 2733
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    invoke-static {v0, v1}, Ljava/util/Arrays;->fill([FF)V

    .line 2734
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub2:[F

    invoke-static {v0, v1}, Ljava/util/Arrays;->fill([FF)V

    .line 2735
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    invoke-static {v0, v1}, Ljava/util/Arrays;->fill([FF)V

    .line 2736
    return-void
.end method

.method public clearRipple()V
    .registers 4

    .prologue
    const/4 v2, 0x0

    .line 1756
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "clearRipple"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1758
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    if-nez v0, :cond_d

    .line 1773
    :cond_c
    :goto_c
    return-void

    .line 1761
    :cond_d
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocity:[F

    if-eqz v0, :cond_c

    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heights:[F

    if-eqz v0, :cond_c

    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocity:[F

    array-length v0, v0

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->max:I

    if-lt v0, v1, :cond_c

    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heights:[F

    array-length v0, v0

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->max:I

    if-lt v0, v1, :cond_c

    .line 1766
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heights:[F

    invoke-static {v0, v2}, Ljava/util/Arrays;->fill([FF)V

    .line 1767
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocity:[F

    invoke-static {v0, v2}, Ljava/util/Arrays;->fill([FF)V

    .line 1768
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub1:[F

    invoke-static {v0, v2}, Ljava/util/Arrays;->fill([FF)V

    .line 1769
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    invoke-static {v0, v2}, Ljava/util/Arrays;->fill([FF)V

    .line 1770
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->heightsSub2:[F

    invoke-static {v0, v2}, Ljava/util/Arrays;->fill([FF)V

    .line 1771
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub2:[F

    invoke-static {v0, v2}, Ljava/util/Arrays;->fill([FF)V

    .line 1772
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    invoke-static {v0, v2}, Ljava/util/Arrays;->fill([FF)V

    goto :goto_c
.end method

.method public destroyed()V
    .registers 3

    .prologue
    .line 2249
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "destroyed"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2251
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    if-eqz v0, :cond_11

    .line 2253
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    const/4 v1, 0x4

    invoke-virtual {v0, v1}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    .line 2256
    :cond_11
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_1b

    .line 2258
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    const/4 v1, 0x5

    invoke-virtual {v0, v1}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    .line 2260
    :cond_1b
    return-void
.end method

.method public getInterpolation70(F)F
    .registers 15
    .param p1, "input"    # F

    .prologue
    const/4 v12, 0x0

    const/high16 v11, 0x3f800000    # 1.0f

    .line 2753
    div-float v0, p1, v11

    .line 2754
    .local v0, "_loc_5":F
    sget-object v6, Lcom/android/internal/policy/impl/keyguard/sec/Value$SineInOut70;->segments:[[F

    array-length v1, v6

    .line 2755
    .local v1, "_loc_6":I
    int-to-float v6, v1

    mul-float/2addr v6, v0

    float-to-double v6, v6

    invoke-static {v6, v7}, Ljava/lang/Math;->floor(D)D

    move-result-wide v6

    double-to-int v4, v6

    .line 2756
    .local v4, "_loc_9":I
    sget-object v6, Lcom/android/internal/policy/impl/keyguard/sec/Value$SineInOut70;->segments:[[F

    array-length v6, v6

    if-lt v4, v6, :cond_1a

    sget-object v6, Lcom/android/internal/policy/impl/keyguard/sec/Value$SineInOut70;->segments:[[F

    array-length v6, v6

    add-int/lit8 v4, v6, -0x1

    .line 2758
    :cond_1a
    int-to-float v6, v4

    int-to-float v7, v1

    div-float v7, v11, v7

    mul-float/2addr v6, v7

    sub-float v6, v0, v6

    int-to-float v7, v1

    mul-float v2, v6, v7

    .line 2759
    .local v2, "_loc_7":F
    sget-object v6, Lcom/android/internal/policy/impl/keyguard/sec/Value$SineInOut70;->segments:[[F

    aget-object v3, v6, v4

    .line 2760
    .local v3, "_loc_8":[F
    const/4 v6, 0x0

    aget v7, v3, v12

    const/high16 v8, 0x40000000    # 2.0f

    sub-float v9, v11, v2

    mul-float/2addr v8, v9

    const/4 v9, 0x1

    aget v9, v3, v9

    aget v10, v3, v12

    sub-float/2addr v9, v10

    mul-float/2addr v8, v9

    const/4 v9, 0x2

    aget v9, v3, v9

    aget v10, v3, v12

    sub-float/2addr v9, v10

    mul-float/2addr v9, v2

    add-float/2addr v8, v9

    mul-float/2addr v8, v2

    add-float/2addr v7, v8

    mul-float/2addr v7, v11

    add-float v5, v6, v7

    .line 2762
    .local v5, "ret":F
    return v5
.end method

.method public getInterpolation80(F)F
    .registers 15
    .param p1, "input"    # F

    .prologue
    const/4 v12, 0x0

    const/high16 v11, 0x3f800000    # 1.0f

    .line 2740
    div-float v0, p1, v11

    .line 2741
    .local v0, "_loc_5":F
    sget-object v6, Lcom/android/internal/policy/impl/keyguard/sec/Value$SineInOut80;->segments:[[F

    array-length v1, v6

    .line 2742
    .local v1, "_loc_6":I
    int-to-float v6, v1

    mul-float/2addr v6, v0

    float-to-double v6, v6

    invoke-static {v6, v7}, Ljava/lang/Math;->floor(D)D

    move-result-wide v6

    double-to-int v4, v6

    .line 2743
    .local v4, "_loc_9":I
    sget-object v6, Lcom/android/internal/policy/impl/keyguard/sec/Value$SineInOut80;->segments:[[F

    array-length v6, v6

    if-lt v4, v6, :cond_1a

    sget-object v6, Lcom/android/internal/policy/impl/keyguard/sec/Value$SineInOut80;->segments:[[F

    array-length v6, v6

    add-int/lit8 v4, v6, -0x1

    .line 2745
    :cond_1a
    int-to-float v6, v4

    int-to-float v7, v1

    div-float v7, v11, v7

    mul-float/2addr v6, v7

    sub-float v6, v0, v6

    int-to-float v7, v1

    mul-float v2, v6, v7

    .line 2746
    .local v2, "_loc_7":F
    sget-object v6, Lcom/android/internal/policy/impl/keyguard/sec/Value$SineInOut80;->segments:[[F

    aget-object v3, v6, v4

    .line 2747
    .local v3, "_loc_8":[F
    const/4 v6, 0x0

    aget v7, v3, v12

    const/high16 v8, 0x40000000    # 2.0f

    sub-float v9, v11, v2

    mul-float/2addr v8, v9

    const/4 v9, 0x1

    aget v9, v3, v9

    aget v10, v3, v12

    sub-float/2addr v9, v10

    mul-float/2addr v8, v9

    const/4 v9, 0x2

    aget v9, v3, v9

    aget v10, v3, v12

    sub-float/2addr v9, v10

    mul-float/2addr v9, v2

    add-float/2addr v8, v9

    mul-float/2addr v8, v2

    add-float/2addr v7, v8

    mul-float/2addr v7, v11

    add-float v5, v6, v7

    .line 2749
    .local v5, "ret":F
    return v5
.end method

.method public getSoundNum()I
    .registers 2

    .prologue
    .line 1387
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->soundNum:I

    return v0
.end method

.method public getSoundTime()I
    .registers 2

    .prologue
    .line 1393
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->soundTime:I

    return v0
.end method

.method public mouseMove(Landroid/view/View;Landroid/view/MotionEvent;)Z
    .registers 17
    .param p1, "view"    # Landroid/view/View;
    .param p2, "event"    # Landroid/view/MotionEvent;

    .prologue
    .line 1065
    const-string v0, "CircleUnlockRippleRenderer"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "event  action: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v2, ", view = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v2, ", src = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v2, "%x"

    const/4 v3, 0x1

    new-array v3, v3, [Ljava/lang/Object;

    const/4 v4, 0x0

    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getSource()I

    move-result v5

    invoke-static {v5}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;

    move-result-object v5

    aput-object v5, v3, v4

    invoke-static {v2, v3}, Ljava/lang/String;->format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1071
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->drawCount:I

    if-nez v0, :cond_51

    .line 1073
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "drawCount == 0 Touch Return"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1074
    const/4 v0, 0x0

    .line 1291
    :goto_50
    return v0

    .line 1077
    :cond_51
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isFirstTouched:Z

    if-eqz v0, :cond_62

    .line 1079
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "isFirstTouched is true"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1080
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->removeDefaultRunnable()V

    .line 1081
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setFalseDefaultEffectFlag()V

    .line 1084
    :cond_62
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getRawX()F

    move-result v0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    .line 1085
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getRawY()F

    move-result v0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseY:F

    .line 1087
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v0, v1, :cond_a2

    .line 1089
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    const/4 v1, -0x1

    if-eq v0, v1, :cond_a2

    .line 1091
    const/4 v13, 0x0

    .line 1092
    .local v13, "returnFlag":Z
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    packed-switch v0, :pswitch_data_366

    .line 1111
    :cond_7f
    :goto_7f
    const/4 v0, 0x1

    if-ne v13, v0, :cond_a2

    .line 1112
    const/4 v0, 0x1

    goto :goto_50

    .line 1097
    :pswitch_84
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    const v1, 0x3e99999a    # 0.3f

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    int-to-float v2, v2

    mul-float/2addr v1, v2

    cmpg-float v0, v0, v1

    if-gtz v0, :cond_7f

    .line 1098
    const/4 v13, 0x1

    goto :goto_7f

    .line 1105
    :pswitch_93
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    const v1, 0x3f333333    # 0.7f

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    int-to-float v2, v2

    mul-float/2addr v1, v2

    cmpl-float v0, v0, v1

    if-ltz v0, :cond_7f

    .line 1106
    const/4 v13, 0x1

    goto :goto_7f

    .line 1116
    .end local v13    # "returnFlag":Z
    :cond_a2
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveReductionRate:F

    const v1, 0x3f70a3d7    # 0.94f

    cmpg-float v0, v0, v1

    if-gez v0, :cond_b0

    .line 1118
    const v0, 0x3f70a3d7    # 0.94f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveReductionRate:F

    .line 1120
    :cond_b0
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getPointerCount()I

    move-result v0

    const/4 v1, 0x1

    if-le v0, v1, :cond_bc

    .line 1122
    const/4 v0, 0x2

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseInputCount:I

    .line 1123
    const/4 v0, 0x0

    goto :goto_50

    .line 1127
    :cond_bc
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseInputCount:I

    const/4 v1, 0x1

    if-le v0, v1, :cond_cc

    .line 1129
    const/4 v0, 0x0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseInputCount:I

    .line 1130
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->downX:F

    .line 1131
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseY:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->downY:F

    .line 1136
    :cond_cc
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLandscape:Z

    if-eqz v0, :cond_24e

    .line 1138
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    int-to-float v1, v1

    const/high16 v2, 0x40000000    # 2.0f

    div-float/2addr v1, v2

    sub-float/2addr v0, v1

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustLandscape:F

    sub-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    .line 1139
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForLandscape:F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    int-to-float v2, v2

    div-float/2addr v1, v2

    mul-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    .line 1140
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    int-to-float v0, v0

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseY:F

    sub-float/2addr v0, v1

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    int-to-float v1, v1

    const/high16 v2, 0x40000000    # 2.0f

    div-float/2addr v1, v2

    sub-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    .line 1141
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    neg-float v0, v0

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForLandscape:F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    int-to-float v2, v2

    div-float/2addr v1, v2

    mul-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    .line 1151
    :goto_104
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-eq v0, v1, :cond_110

    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v0, v1, :cond_138

    .line 1153
    :cond_110
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getPressure()F

    move-result v12

    .line 1155
    .local v12, "pressure":F
    float-to-double v0, v12

    const-wide/high16 v2, 0x3ff0000000000000L    # 1.0

    cmpl-double v0, v0, v2

    if-ltz v0, :cond_11d

    .line 1157
    const/high16 v12, 0x3f800000    # 1.0f

    .line 1160
    :cond_11d
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    const/4 v1, 0x3

    if-eq v0, v1, :cond_12b

    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    const/4 v1, 0x1

    if-ne v0, v1, :cond_284

    .line 1162
    :cond_12b
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    float-to-int v0, v0

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseY:F

    float-to-int v1, v1

    const/4 v2, 0x1

    invoke-static {v0, v1, v2, v12}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onTouch(IIIF)V

    .line 1163
    const/4 v0, 0x1

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isMakedASpenToucdUp:Z

    .line 1172
    .end local v12    # "pressure":F
    :cond_138
    :goto_138
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    if-nez v0, :cond_2a0

    .line 1175
    const/4 v0, 0x0

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setHoverEnable(Z)V

    .line 1176
    const/4 v0, 0x1

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isTouched:Z

    .line 1177
    const/4 v0, 0x0

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChanged:Z

    .line 1179
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    if-nez v0, :cond_196

    .line 1181
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "show mSoundPool is null"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1183
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-static {v0}, Lcom/android/internal/policy/impl/keyguard/KeyguardUpdateMonitor;->getInstance(Landroid/content/Context;)Lcom/android/internal/policy/impl/keyguard/KeyguardUpdateMonitor;

    move-result-object v0

    invoke-virtual {v0}, Lcom/android/internal/policy/impl/keyguard/KeyguardUpdateMonitor;->hasBootCompleted()Z

    move-result v0

    if-eqz v0, :cond_196

    .line 1185
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "KeyguardUpdateMonitor hasBootCompleted"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1186
    new-instance v0, Landroid/media/SoundPool;

    const/16 v1, 0xa

    const/4 v2, 0x1

    const/4 v3, 0x0

    invoke-direct {v0, v1, v2, v3}, Landroid/media/SoundPool;-><init>(III)V

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    .line 1187
    const/4 v0, 0x2

    new-array v0, v0, [I

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds:[I

    .line 1188
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds:[I

    const/4 v1, 0x0

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    iget v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRDownId:I

    const/4 v5, 0x1

    invoke-virtual {v2, v3, v4, v5}, Landroid/media/SoundPool;->load(Landroid/content/Context;II)I

    move-result v2

    aput v2, v0, v1

    .line 1189
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->sounds:[I

    const/4 v1, 0x1

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSoundPool:Landroid/media/SoundPool;

    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    iget v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRUpId:I

    const/4 v5, 0x1

    invoke-virtual {v2, v3, v4, v5}, Landroid/media/SoundPool;->load(Landroid/content/Context;II)I

    move-result v2

    aput v2, v0, v1

    .line 1193
    :cond_196
    const/4 v0, 0x0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseInputCount:I

    .line 1194
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->downX:F

    .line 1195
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseY:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->downY:F

    .line 1196
    const/4 v0, 0x0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rippleDistance:I

    .line 1197
    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J

    move-result-wide v0

    iput-wide v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->prevPressTime:J

    .line 1198
    const-wide/16 v0, 0x0

    iput-wide v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->diffPressTime:J

    .line 1200
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForRipple:F

    const/high16 v3, 0x40800000    # 4.0f

    mul-float/2addr v2, v3

    const/4 v3, 0x1

    invoke-direct {p0, v0, v1, v2, v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ripple(FFFZ)V

    .line 1201
    const/4 v0, 0x0

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->playSound(I)V

    .line 1242
    :cond_1bf
    :goto_1bf
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-eq v0, v1, :cond_1d1

    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-eq v0, v1, :cond_1d1

    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v0, v1, :cond_24b

    .line 1246
    :cond_1d1
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    const/16 v1, 0x9

    if-ne v0, v1, :cond_1e0

    .line 1248
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "========================= ACTION_HOVER_ENTER"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1250
    :cond_1e0
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    const/4 v1, 0x7

    if-ne v0, v1, :cond_354

    .line 1252
    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J

    move-result-wide v10

    .line 1254
    .local v10, "hoverMoveTime":J
    iget-wide v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mPreviousRippleTime:J

    sub-long v0, v10, v0

    const-wide/16 v2, 0x640

    cmp-long v0, v0, v2

    if-lez v0, :cond_204

    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mHoverEnabled:Z

    if-nez v0, :cond_204

    .line 1256
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "setHoverEnable true ======================="

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1257
    const/4 v0, 0x1

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setHoverEnable(Z)V

    .line 1260
    :cond_204
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mHoverEnabled:Z

    if-eqz v0, :cond_241

    .line 1262
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mFresnelRatio:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->hoverPlus_Frenel:F

    add-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mFresnelRatio:F

    .line 1263
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSpecularRatio:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->hoverPlus_Specular:F

    add-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSpecularRatio:F

    .line 1264
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mExponentRatio:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->hoverPlus_exponent:F

    add-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mExponentRatio:F

    .line 1266
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mFresnelRatio:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_FRESENL_MAX:F

    cmpl-float v0, v0, v1

    if-lez v0, :cond_229

    .line 1268
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_FRESENL_MAX:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mFresnelRatio:F

    .line 1271
    :cond_229
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSpecularRatio:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_SPECULAR_RATIO_MAX:F

    cmpl-float v0, v0, v1

    if-lez v0, :cond_235

    .line 1273
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_SPECULAR_RATIO_MAX:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSpecularRatio:F

    .line 1276
    :cond_235
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mExponentRatio:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_EXPONENT_RATIO_MAX:F

    cmpl-float v0, v0, v1

    if-lez v0, :cond_241

    .line 1278
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->HOVER_EXPONENT_RATIO_MAX:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mExponentRatio:F

    .line 1283
    :cond_241
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mHoverIntensity:F

    const/4 v3, 0x0

    invoke-direct {p0, v0, v1, v2, v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ripple(FFFZ)V

    .line 1291
    .end local v10    # "hoverMoveTime":J
    :cond_24b
    :goto_24b
    const/4 v0, 0x0

    goto/16 :goto_50

    .line 1145
    :cond_24e
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    int-to-float v1, v1

    const/high16 v2, 0x40000000    # 2.0f

    div-float/2addr v1, v2

    sub-float/2addr v0, v1

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustPortrait:F

    sub-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    .line 1146
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForPortrait:F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    int-to-float v2, v2

    div-float/2addr v1, v2

    mul-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    .line 1147
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    int-to-float v0, v0

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseY:F

    sub-float/2addr v0, v1

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    int-to-float v1, v1

    const/high16 v2, 0x40000000    # 2.0f

    div-float/2addr v1, v2

    sub-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    .line 1148
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    neg-float v0, v0

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForPortrait:F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    int-to-float v2, v2

    div-float/2addr v1, v2

    mul-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    goto/16 :goto_104

    .line 1165
    .restart local v12    # "pressure":F
    :cond_284
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getSource()I

    move-result v0

    and-int/lit16 v0, v0, 0x4002

    const/16 v1, 0x4002

    if-ne v0, v1, :cond_138

    .line 1167
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    float-to-int v0, v0

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseY:F

    float-to-int v1, v1

    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v2

    invoke-static {v0, v1, v2, v12}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onTouch(IIIF)V

    .line 1168
    const/4 v0, 0x0

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isMakedASpenToucdUp:Z

    goto/16 :goto_138

    .line 1203
    .end local v12    # "pressure":F
    :cond_2a0
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    const/4 v1, 0x2

    if-ne v0, v1, :cond_30c

    .line 1205
    const/4 v0, 0x0

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setHoverEnable(Z)V

    .line 1206
    const/4 v0, 0x1

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isTouched:Z

    .line 1207
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->downX:F

    sub-float v8, v0, v1

    .line 1208
    .local v8, "dx":F
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseY:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->downY:F

    sub-float v9, v0, v1

    .line 1209
    .local v9, "dy":F
    float-to-double v0, v8

    const-wide/high16 v2, 0x4000000000000000L    # 2.0

    invoke-static {v0, v1, v2, v3}, Ljava/lang/Math;->pow(DD)D

    move-result-wide v0

    float-to-double v2, v9

    const-wide/high16 v4, 0x4000000000000000L    # 2.0

    invoke-static {v2, v3, v4, v5}, Ljava/lang/Math;->pow(DD)D

    move-result-wide v2

    add-double/2addr v0, v2

    invoke-static {v0, v1}, Ljava/lang/Math;->sqrt(D)D

    move-result-wide v0

    double-to-int v7, v0

    .line 1210
    .local v7, "distForwWave":I
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rippleDistance:I

    add-int/2addr v0, v7

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rippleDistance:I

    .line 1211
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseX:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->downX:F

    .line 1212
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseY:F

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->downY:F

    .line 1214
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rippleDistance:I

    int-to-double v0, v0

    iget-wide v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rippleDragThreshold:D

    cmpl-double v0, v0, v2

    if-lez v0, :cond_1bf

    .line 1215
    const/4 v0, 0x0

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setHoverEnable(Z)V

    .line 1216
    const/4 v0, 0x0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rippleDistance:I

    .line 1218
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForRipple:F

    const/high16 v3, 0x40400000    # 3.0f

    mul-float/2addr v2, v3

    const/4 v3, 0x1

    invoke-direct {p0, v0, v1, v2, v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ripple(FFFZ)V

    .line 1219
    const/4 v0, 0x1

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->playDragSound(I)V

    .line 1220
    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    const-wide/16 v4, 0x14

    const/high16 v6, 0x40400000    # 3.0f

    move-object v0, p0

    invoke-virtual/range {v0 .. v6}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->startLongPressCheck2(Landroid/view/View;FFJF)V

    goto/16 :goto_1bf

    .line 1223
    .end local v7    # "distForwWave":I
    .end local v8    # "dx":F
    .end local v9    # "dy":F
    :cond_30c
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    const/4 v1, 0x1

    if-ne v0, v1, :cond_341

    .line 1225
    const/4 v0, 0x0

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setHoverEnable(Z)V

    .line 1226
    const/4 v0, 0x0

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isTouched:Z

    .line 1228
    invoke-static {}, Landroid/os/SystemClock;->uptimeMillis()J

    move-result-wide v0

    iget-wide v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->prevPressTime:J

    sub-long/2addr v0, v2

    iput-wide v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->diffPressTime:J

    .line 1229
    const/4 v0, 0x0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseInputCount:I

    .line 1231
    iget-wide v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->diffPressTime:J

    const-wide/16 v2, 0x258

    cmp-long v0, v0, v2

    if-lez v0, :cond_1bf

    .line 1233
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForRipple:F

    const/high16 v3, 0x40800000    # 4.0f

    mul-float/2addr v2, v3

    const/4 v3, 0x1

    invoke-direct {p0, v0, v1, v2, v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ripple(FFFZ)V

    .line 1234
    const/4 v0, 0x0

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->playSound(I)V

    goto/16 :goto_1bf

    .line 1236
    :cond_341
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    const/4 v1, 0x3

    if-ne v0, v1, :cond_1bf

    .line 1237
    const/4 v0, 0x0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseInputCount:I

    .line 1238
    const/4 v0, 0x0

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isTouched:Z

    .line 1239
    const/4 v0, 0x0

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setHoverEnable(Z)V

    goto/16 :goto_1bf

    .line 1286
    :cond_354
    invoke-virtual/range {p2 .. p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v0

    const/16 v1, 0xa

    if-ne v0, v1, :cond_24b

    .line 1288
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "ACTION_HOVER_EXIT"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto/16 :goto_24b

    .line 1092
    nop

    :pswitch_data_366
    .packed-switch 0x0
        :pswitch_84
        :pswitch_84
        :pswitch_93
        :pswitch_93
    .end packed-switch
.end method

.method public onConfigurationChanged()V
    .registers 4

    .prologue
    const/4 v2, 0x1

    .line 1055
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "= onConfigurationChanged = Renderer onConfigurationChanged"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1056
    iput-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChanged:Z

    .line 1057
    const/4 v0, 0x0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChangCount:I

    .line 1058
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v0, Landroid/opengl/GLSurfaceView;

    invoke-virtual {v0, v2}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 1059
    return-void
.end method

.method public onDrawFrame(Ljavax/microedition/khronos/opengles/GL10;)V
    .registers 32
    .param p1, "gl"    # Ljavax/microedition/khronos/opengles/GL10;

    .prologue
    .line 954
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->drawCount:I

    if-nez v1, :cond_16

    move-object/from16 v0, p0

    iget-boolean v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isShowCalled:Z

    if-nez v1, :cond_16

    .line 956
    const-string v1, "CircleUnlockRippleRenderer"

    const-string v2, "onDrawFrame call setRippleVersion"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 957
    invoke-direct/range {p0 .. p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setRippleVersion()V

    .line 960
    :cond_16
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBgChangeCheckArray:Ljava/util/ArrayList;

    invoke-virtual {v1}, Ljava/util/ArrayList;->size()I

    move-result v1

    if-eqz v1, :cond_15e

    .line 962
    const-string v1, "CircleUnlockRippleRenderer"

    const-string v2, "Change opengl BG Texture"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 963
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onFreeBGTextures()V

    .line 964
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onLoadBGTextures()V

    .line 965
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBgChangeCheckArray:Ljava/util/ArrayList;

    const/4 v2, 0x0

    invoke-virtual {v1, v2}, Ljava/util/ArrayList;->remove(I)Ljava/lang/Object;

    .line 974
    :cond_35
    :goto_35
    move-object/from16 v0, p0

    iget-boolean v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLandscape:Z

    if-nez v1, :cond_206

    .line 976
    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v1, v2, :cond_16f

    .line 977
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->vertices:[F

    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    move-object/from16 v0, p0

    iget-object v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->indices:[S

    move-object/from16 v0, p0

    iget-object v4, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->vertices:[F

    array-length v4, v4

    move-object/from16 v0, p0

    iget-object v5, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    array-length v5, v5

    move-object/from16 v0, p0

    iget-object v6, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->indices:[S

    array-length v6, v6

    move-object/from16 v0, p0

    iget-object v7, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    move-object/from16 v0, p0

    iget v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    int-to-float v8, v8

    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBitmapRatio:F

    div-float/2addr v8, v9

    float-to-int v8, v8

    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    div-int/lit8 v10, v10, 0x2

    move-object/from16 v0, p0

    iget v11, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v11, v11, 0x2

    move-object/from16 v0, p0

    iget v12, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->refractiveIndex:F

    move-object/from16 v0, p0

    iget v13, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->reflectionRatio:F

    move-object/from16 v0, p0

    iget v14, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->alphaRatio1:F

    move-object/from16 v0, p0

    iget v15, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->alphaRatio2:F

    move-object/from16 v0, p0

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    move-object/from16 v16, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    move/from16 v17, v0

    aget-object v16, v16, v17

    const/16 v17, 0x0

    aget v16, v16, v17

    move-object/from16 v0, p0

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    move-object/from16 v17, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    move/from16 v18, v0

    aget-object v17, v17, v18

    const/16 v18, 0x1

    aget v17, v17, v18

    move-object/from16 v0, p0

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    move-object/from16 v18, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    move/from16 v19, v0

    aget-object v18, v18, v19

    const/16 v19, 0x2

    aget v18, v18, v19

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mFresnelRatio:F

    move/from16 v19, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSpecularRatio:F

    move/from16 v20, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mExponentRatio:F

    move/from16 v21, v0

    const/16 v22, 0x0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    move/from16 v23, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    move/from16 v24, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeMix:F

    move/from16 v25, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ReferencePoint:F

    move/from16 v26, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TexMoveU:F

    move/from16 v27, v0

    move-object/from16 v0, p0

    iget-boolean v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bGravityDirection:Z

    move/from16 v28, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    move/from16 v29, v0

    invoke-static/range {v1 .. v29}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onDrawGravity([F[F[SIII[FIIIIFFFFFFFFFFIFFFFFZF)V

    .line 1028
    :goto_102
    move-object/from16 v0, p0

    iget-boolean v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChanged:Z

    if-eqz v1, :cond_138

    .line 1030
    move-object/from16 v0, p0

    iget-boolean v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isSurfaceChanged:Z

    const/4 v2, 0x1

    if-eq v1, v2, :cond_117

    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChangCount:I

    const/16 v2, 0x14

    if-le v1, v2, :cond_366

    .line 1032
    :cond_117
    const-string v1, "CircleUnlockRippleRenderer"

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "= onConfigurationChanged = onDrawFrame isSurfaceChanged == true && isOrientationChanged == true, isOrientationChangCount = "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChangCount:I

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1033
    const/4 v1, 0x0

    move-object/from16 v0, p0

    iput-boolean v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChanged:Z

    .line 1041
    :cond_138
    :goto_138
    const/4 v1, 0x0

    move-object/from16 v0, p0

    iput-boolean v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isSurfaceChanged:Z

    .line 1043
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->drawCount:I

    if-lez v1, :cond_14c

    move-object/from16 v0, p0

    iget-boolean v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChanged:Z

    if-nez v1, :cond_14c

    .line 1044
    invoke-direct/range {p0 .. p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->move()V

    .line 1046
    :cond_14c
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->drawCount:I

    const/4 v2, 0x2

    if-ge v1, v2, :cond_15d

    .line 1048
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->drawCount:I

    add-int/lit8 v1, v1, 0x1

    move-object/from16 v0, p0

    iput v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->drawCount:I

    .line 1051
    :cond_15d
    return-void

    .line 967
    :cond_15e
    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v1, v2, :cond_35

    .line 969
    invoke-virtual/range {p0 .. p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->updateGravityRippleEffect()V

    .line 970
    invoke-virtual/range {p0 .. p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->updateBGTiltAnimation()V

    .line 971
    invoke-virtual/range {p0 .. p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->updateCausticsMixRatio()V

    goto/16 :goto_35

    .line 989
    :cond_16f
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->vertices:[F

    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    move-object/from16 v0, p0

    iget-object v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->indices:[S

    move-object/from16 v0, p0

    iget-object v4, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->vertices:[F

    array-length v4, v4

    move-object/from16 v0, p0

    iget-object v5, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    array-length v5, v5

    move-object/from16 v0, p0

    iget-object v6, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->indices:[S

    array-length v6, v6

    move-object/from16 v0, p0

    iget-object v7, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    move-object/from16 v0, p0

    iget v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    int-to-float v8, v8

    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBitmapRatio:F

    div-float/2addr v8, v9

    float-to-int v8, v8

    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    div-int/lit8 v10, v10, 0x2

    move-object/from16 v0, p0

    iget v11, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v11, v11, 0x2

    move-object/from16 v0, p0

    iget v12, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->refractiveIndex:F

    move-object/from16 v0, p0

    iget v13, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->reflectionRatio:F

    move-object/from16 v0, p0

    iget v14, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->alphaRatio1:F

    move-object/from16 v0, p0

    iget v15, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->alphaRatio2:F

    move-object/from16 v0, p0

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    move-object/from16 v16, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    move/from16 v17, v0

    aget-object v16, v16, v17

    const/16 v17, 0x0

    aget v16, v16, v17

    move-object/from16 v0, p0

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    move-object/from16 v17, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    move/from16 v18, v0

    aget-object v17, v17, v18

    const/16 v18, 0x1

    aget v17, v17, v18

    move-object/from16 v0, p0

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    move-object/from16 v18, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    move/from16 v19, v0

    aget-object v18, v18, v19

    const/16 v19, 0x2

    aget v18, v18, v19

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mFresnelRatio:F

    move/from16 v19, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSpecularRatio:F

    move/from16 v20, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mExponentRatio:F

    move/from16 v21, v0

    invoke-static/range {v1 .. v21}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onDraw([F[F[SIII[FIIIIFFFFFFFFFF)V

    goto/16 :goto_102

    .line 1001
    :cond_206
    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v1, v2, :cond_2cf

    .line 1002
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->vertices:[F

    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    move-object/from16 v0, p0

    iget-object v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->indices:[S

    move-object/from16 v0, p0

    iget-object v4, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->vertices:[F

    array-length v4, v4

    move-object/from16 v0, p0

    iget-object v5, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    array-length v5, v5

    move-object/from16 v0, p0

    iget-object v6, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->indices:[S

    array-length v6, v6

    move-object/from16 v0, p0

    iget-object v7, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    move-object/from16 v0, p0

    iget v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    int-to-float v9, v9

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBitmapRatio:F

    mul-float/2addr v9, v10

    float-to-int v9, v9

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    div-int/lit8 v10, v10, 0x2

    move-object/from16 v0, p0

    iget v11, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v11, v11, 0x2

    move-object/from16 v0, p0

    iget v12, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->refractiveIndex:F

    move-object/from16 v0, p0

    iget v13, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->reflectionRatio:F

    move-object/from16 v0, p0

    iget v14, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->alphaRatio1:F

    move-object/from16 v0, p0

    iget v15, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->alphaRatio2:F

    move-object/from16 v0, p0

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    move-object/from16 v16, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    move/from16 v17, v0

    aget-object v16, v16, v17

    const/16 v17, 0x0

    aget v16, v16, v17

    move-object/from16 v0, p0

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    move-object/from16 v17, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    move/from16 v18, v0

    aget-object v17, v17, v18

    const/16 v18, 0x1

    aget v17, v17, v18

    move-object/from16 v0, p0

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    move-object/from16 v18, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    move/from16 v19, v0

    aget-object v18, v18, v19

    const/16 v19, 0x2

    aget v18, v18, v19

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mFresnelRatio:F

    move/from16 v19, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSpecularRatio:F

    move/from16 v20, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mExponentRatio:F

    move/from16 v21, v0

    const/16 v22, 0x0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    move/from16 v23, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    move/from16 v24, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeMix:F

    move/from16 v25, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ReferencePoint:F

    move/from16 v26, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TexMoveU:F

    move/from16 v27, v0

    move-object/from16 v0, p0

    iget-boolean v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bGravityDirection:Z

    move/from16 v28, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    move/from16 v29, v0

    invoke-static/range {v1 .. v29}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onDrawGravity([F[F[SIII[FIIIIFFFFFFFFFFIFFFFFZF)V

    goto/16 :goto_102

    .line 1015
    :cond_2cf
    move-object/from16 v0, p0

    iget-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->vertices:[F

    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    move-object/from16 v0, p0

    iget-object v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->indices:[S

    move-object/from16 v0, p0

    iget-object v4, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->vertices:[F

    array-length v4, v4

    move-object/from16 v0, p0

    iget-object v5, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuHeights:[F

    array-length v5, v5

    move-object/from16 v0, p0

    iget-object v6, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->indices:[S

    array-length v6, v6

    move-object/from16 v0, p0

    iget-object v7, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    move-object/from16 v0, p0

    iget v8, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_WIDTH:I

    move-object/from16 v0, p0

    iget v9, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MESH_SIZE_HEIGHT:I

    int-to-float v9, v9

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBitmapRatio:F

    mul-float/2addr v9, v10

    float-to-int v9, v9

    move-object/from16 v0, p0

    iget v10, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    div-int/lit8 v10, v10, 0x2

    move-object/from16 v0, p0

    iget v11, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v11, v11, 0x2

    move-object/from16 v0, p0

    iget v12, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->refractiveIndex:F

    move-object/from16 v0, p0

    iget v13, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->reflectionRatio:F

    move-object/from16 v0, p0

    iget v14, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->alphaRatio1:F

    move-object/from16 v0, p0

    iget v15, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->alphaRatio2:F

    move-object/from16 v0, p0

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    move-object/from16 v16, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    move/from16 v17, v0

    aget-object v16, v16, v17

    const/16 v17, 0x0

    aget v16, v16, v17

    move-object/from16 v0, p0

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    move-object/from16 v17, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    move/from16 v18, v0

    aget-object v17, v17, v18

    const/16 v18, 0x1

    aget v17, v17, v18

    move-object/from16 v0, p0

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->inkColorFromSetting:[[F

    move-object/from16 v18, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mInkEffectColor:I

    move/from16 v19, v0

    aget-object v18, v18, v19

    const/16 v19, 0x2

    aget v18, v18, v19

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mFresnelRatio:F

    move/from16 v19, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSpecularRatio:F

    move/from16 v20, v0

    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mExponentRatio:F

    move/from16 v21, v0

    invoke-static/range {v1 .. v21}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onDraw([F[F[SIII[FIIIIFFFFFFFFFF)V

    goto/16 :goto_102

    .line 1037
    :cond_366
    move-object/from16 v0, p0

    iget v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChangCount:I

    add-int/lit8 v1, v1, 0x1

    move-object/from16 v0, p0

    iput v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChangCount:I

    goto/16 :goto_138
.end method

.method public onMakeLeftDirectionEndRipple()V
    .registers 9

    .prologue
    .line 2690
    const/4 v0, 0x0

    .line 2691
    .local v0, "EndshiftVal":I
    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v1, v3, 0x2

    .local v1, "i":I
    :goto_5
    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    sub-int/2addr v3, v0

    if-ge v1, v3, :cond_39

    .line 2692
    const/4 v2, 0x0

    .local v2, "j":I
    :goto_b
    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    if-ge v2, v3, :cond_36

    .line 2693
    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    iget v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    add-int v5, v1, v0

    sub-int/2addr v4, v5

    add-int/lit8 v4, v4, -0x1

    iget v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v4, v5

    add-int/2addr v4, v2

    sget-object v5, Lcom/android/internal/policy/impl/keyguard/sec/Value$SideWave;->velocity:[F

    iget v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v6, v6, 0x2

    sub-int v6, v1, v6

    iget v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v6, v7

    add-int/2addr v6, v2

    aget v5, v5, v6

    const/high16 v6, 0x40400000    # 3.0f

    mul-float/2addr v5, v6

    const v6, 0x3a83126f    # 0.001f

    mul-float/2addr v5, v6

    aput v5, v3, v4

    .line 2692
    add-int/lit8 v2, v2, 0x1

    goto :goto_b

    .line 2691
    :cond_36
    add-int/lit8 v1, v1, 0x1

    goto :goto_5

    .line 2697
    .end local v2    # "j":I
    :cond_39
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v3

    iput-wide v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveTime:J

    .line 2698
    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v3, Landroid/opengl/GLSurfaceView;

    const/4 v4, 0x1

    invoke-virtual {v3, v4}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 2699
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v3

    iput-wide v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveTime:J

    .line 2700
    return-void
.end method

.method public onMakeLeftDirectionStartRipple()V
    .registers 10

    .prologue
    const/high16 v8, 0x40400000    # 3.0f

    const v7, 0x3fd9999a    # 1.7f

    .line 2597
    const/4 v0, 0x0

    .local v0, "i":I
    :goto_6
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v2, v2, 0x2

    if-ge v0, v2, :cond_29

    .line 2598
    const/4 v1, 0x0

    .local v1, "j":I
    :goto_d
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    if-ge v1, v2, :cond_26

    .line 2599
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v3, v0

    add-int/2addr v3, v1

    sget-object v4, Lcom/android/internal/policy/impl/keyguard/sec/Value$TotalWave1;->velocity:[F

    iget v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v5, v0

    add-int/2addr v5, v1

    aget v4, v4, v5

    mul-float/2addr v4, v8

    mul-float/2addr v4, v7

    aput v4, v2, v3

    .line 2598
    add-int/lit8 v1, v1, 0x1

    goto :goto_d

    .line 2597
    :cond_26
    add-int/lit8 v0, v0, 0x1

    goto :goto_6

    .line 2603
    .end local v1    # "j":I
    :cond_29
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v0, v2, 0x2

    :goto_2d
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    if-ge v0, v2, :cond_54

    .line 2604
    const/4 v1, 0x0

    .restart local v1    # "j":I
    :goto_32
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    if-ge v1, v2, :cond_51

    .line 2605
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v3, v0

    add-int/2addr v3, v1

    sget-object v4, Lcom/android/internal/policy/impl/keyguard/sec/Value$TotalWave2;->velocity:[F

    iget v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v5, v5, 0x2

    sub-int v5, v0, v5

    iget v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v5, v6

    add-int/2addr v5, v1

    aget v4, v4, v5

    mul-float/2addr v4, v8

    mul-float/2addr v4, v7

    aput v4, v2, v3

    .line 2604
    add-int/lit8 v1, v1, 0x1

    goto :goto_32

    .line 2603
    :cond_51
    add-int/lit8 v0, v0, 0x1

    goto :goto_2d

    .line 2609
    .end local v1    # "j":I
    :cond_54
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v2, Landroid/opengl/GLSurfaceView;

    const/4 v3, 0x1

    invoke-virtual {v2, v3}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 2610
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v2

    iput-wide v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveTime:J

    .line 2614
    const v2, 0x3ea8f5c3    # 0.33f

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 2615
    const/high16 v2, 0x42200000    # 40.0f

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ReferencePoint:F

    .line 2616
    return-void
.end method

.method public onMakeRightDirectionEndRipple()V
    .registers 9

    .prologue
    .line 2673
    const/4 v0, 0x0

    .line 2674
    .local v0, "EndshiftVal":I
    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v1, v3, 0x2

    .local v1, "i":I
    :goto_5
    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    sub-int/2addr v3, v0

    if-ge v1, v3, :cond_34

    .line 2675
    const/4 v2, 0x0

    .local v2, "j":I
    :goto_b
    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    if-ge v2, v3, :cond_31

    .line 2676
    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    add-int v4, v1, v0

    iget v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v4, v5

    add-int/2addr v4, v2

    sget-object v5, Lcom/android/internal/policy/impl/keyguard/sec/Value$SideWave;->velocity:[F

    iget v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v6, v6, 0x2

    sub-int v6, v1, v6

    iget v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v6, v7

    add-int/2addr v6, v2

    aget v5, v5, v6

    const/high16 v6, 0x40400000    # 3.0f

    mul-float/2addr v5, v6

    const v6, 0x3c23d70a    # 0.01f

    mul-float/2addr v5, v6

    aput v5, v3, v4

    .line 2675
    add-int/lit8 v2, v2, 0x1

    goto :goto_b

    .line 2674
    :cond_31
    add-int/lit8 v1, v1, 0x1

    goto :goto_5

    .line 2680
    .end local v2    # "j":I
    :cond_34
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v3

    iput-wide v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveTime:J

    .line 2682
    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v3, Landroid/opengl/GLSurfaceView;

    const/4 v4, 0x1

    invoke-virtual {v3, v4}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 2683
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v3

    iput-wide v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveTime:J

    .line 2684
    return-void
.end method

.method public onMakeRightDirectionStartRipple()V
    .registers 10

    .prologue
    const/high16 v8, 0x40400000    # 3.0f

    const v7, 0x3fd9999a    # 1.7f

    .line 2569
    const/4 v0, 0x0

    .local v0, "i":I
    :goto_6
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v2, v2, 0x2

    if-ge v0, v2, :cond_2e

    .line 2570
    const/4 v1, 0x0

    .local v1, "j":I
    :goto_d
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    if-ge v1, v2, :cond_2b

    .line 2571
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    sub-int/2addr v3, v0

    add-int/lit8 v3, v3, -0x1

    iget v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v3, v4

    add-int/2addr v3, v1

    sget-object v4, Lcom/android/internal/policy/impl/keyguard/sec/Value$TotalWave1;->velocity:[F

    iget v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v5, v0

    add-int/2addr v5, v1

    aget v4, v4, v5

    mul-float/2addr v4, v8

    mul-float/2addr v4, v7

    aput v4, v2, v3

    .line 2570
    add-int/lit8 v1, v1, 0x1

    goto :goto_d

    .line 2569
    :cond_2b
    add-int/lit8 v0, v0, 0x1

    goto :goto_6

    .line 2575
    .end local v1    # "j":I
    :cond_2e
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v0, v2, 0x2

    :goto_32
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    if-ge v0, v2, :cond_5e

    .line 2576
    const/4 v1, 0x0

    .restart local v1    # "j":I
    :goto_37
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    if-ge v1, v2, :cond_5b

    .line 2577
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->velocitySub1:[F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    sub-int/2addr v3, v0

    add-int/lit8 v3, v3, -0x1

    iget v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v3, v4

    add-int/2addr v3, v1

    sget-object v4, Lcom/android/internal/policy/impl/keyguard/sec/Value$TotalWave2;->velocity:[F

    iget v5, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_HEIGHT:I

    div-int/lit8 v5, v5, 0x2

    sub-int v5, v0, v5

    iget v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->NUM_DETAILS_WIDTH:I

    mul-int/2addr v5, v6

    add-int/2addr v5, v1

    aget v4, v4, v5

    mul-float/2addr v4, v8

    mul-float/2addr v4, v7

    aput v4, v2, v3

    .line 2576
    add-int/lit8 v1, v1, 0x1

    goto :goto_37

    .line 2575
    :cond_5b
    add-int/lit8 v0, v0, 0x1

    goto :goto_32

    .line 2581
    .end local v1    # "j":I
    :cond_5e
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v2

    iput-wide v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveTime:J

    .line 2583
    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    check-cast v2, Landroid/opengl/GLSurfaceView;

    const/4 v3, 0x1

    invoke-virtual {v2, v3}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 2584
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v2

    iput-wide v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveTime:J

    .line 2587
    const v2, 0x3ea8f5c3    # 0.33f

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 2588
    const/high16 v2, 0x42200000    # 40.0f

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ReferencePoint:F

    .line 2589
    return-void
.end method

.method public onSContextChanged(Landroid/hardware/scontext/SContextEvent;)V
    .registers 10
    .param p1, "event"    # Landroid/hardware/scontext/SContextEvent;

    .prologue
    const/4 v4, 0x1

    const/4 v5, 0x0

    .line 497
    iget-object v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v6}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v6

    const-string v7, "master_motion"

    invoke-static {v6, v7, v5}, Landroid/provider/Settings$System;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I

    move-result v6

    if-ne v6, v4, :cond_25

    move v1, v4

    .line 498
    .local v1, "mMasterArcMotion":Z
    :goto_11
    iget-object v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v6}, Landroid/content/Context;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v6

    const-string v7, "arc_motion_ripple_effect"

    invoke-static {v6, v7, v5}, Landroid/provider/Settings$System;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;I)I

    move-result v6

    if-ne v6, v4, :cond_27

    move v2, v4

    .line 499
    .local v2, "mRippleEffect":Z
    :goto_20
    if-eqz v1, :cond_24

    if-nez v2, :cond_29

    .line 543
    :cond_24
    :goto_24
    return-void

    .end local v1    # "mMasterArcMotion":Z
    .end local v2    # "mRippleEffect":Z
    :cond_25
    move v1, v5

    .line 497
    goto :goto_11

    .restart local v1    # "mMasterArcMotion":Z
    :cond_27
    move v2, v5

    .line 498
    goto :goto_20

    .line 503
    .restart local v2    # "mRippleEffect":Z
    :cond_29
    iget-object v3, p1, Landroid/hardware/scontext/SContextEvent;->scontext:Landroid/hardware/scontext/SContext;

    .line 504
    .local v3, "scontext":Landroid/hardware/scontext/SContext;
    invoke-virtual {v3}, Landroid/hardware/scontext/SContext;->getType()I

    move-result v6

    const/16 v7, 0x12

    if-ne v6, v7, :cond_24

    .line 506
    invoke-virtual {p1}, Landroid/hardware/scontext/SContextEvent;->getBounceLongMotionContext()Landroid/hardware/scontext/SContextBounceLongMotion;

    move-result-object v0

    .line 509
    .local v0, "bounceLongMotionContext":Landroid/hardware/scontext/SContextBounceLongMotion;
    invoke-virtual {v0}, Landroid/hardware/scontext/SContextBounceLongMotion;->getAction()I

    move-result v6

    packed-switch v6, :pswitch_data_b6

    goto :goto_24

    .line 511
    :pswitch_3f
    const-string v4, "CircleUnlockRippleRenderer"

    new-instance v5, Ljava/lang/StringBuilder;

    invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V

    const-string v6, "BOUNCE_LONG_NONE type"

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v5

    iget v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v5

    invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v5

    invoke-static {v4, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_24

    .line 514
    :pswitch_5a
    const-string v5, "CircleUnlockRippleRenderer"

    new-instance v6, Ljava/lang/StringBuilder;

    invoke-direct {v6}, Ljava/lang/StringBuilder;-><init>()V

    const-string v7, "BOUNCE_LONG_RIGHT type"

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v6

    iget v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v6

    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v6

    invoke-static {v5, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 515
    invoke-virtual {p0, v4}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->EffectEnable(I)V

    goto :goto_24

    .line 518
    :pswitch_78
    const-string v4, "CircleUnlockRippleRenderer"

    new-instance v6, Ljava/lang/StringBuilder;

    invoke-direct {v6}, Ljava/lang/StringBuilder;-><init>()V

    const-string v7, "BOUNCE_LONG_LEFT type"

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v6

    iget v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v6

    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v6

    invoke-static {v4, v6}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 519
    invoke-virtual {p0, v5}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->EffectEnable(I)V

    goto :goto_24

    .line 522
    :pswitch_96
    const-string v4, "CircleUnlockRippleRenderer"

    new-instance v5, Ljava/lang/StringBuilder;

    invoke-direct {v5}, Ljava/lang/StringBuilder;-><init>()V

    const-string v6, "BOUNCE_LONG_UNHAND type"

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v5

    iget v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    invoke-virtual {v5, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v5

    invoke-virtual {v5}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v5

    invoke-static {v4, v5}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 523
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->EffectDisable()V

    goto/16 :goto_24

    .line 509
    nop

    :pswitch_data_b6
    .packed-switch 0x0
        :pswitch_3f
        :pswitch_5a
        :pswitch_78
        :pswitch_96
    .end packed-switch
.end method

.method public onSurfaceChanged(Ljavax/microedition/khronos/opengles/GL10;II)V
    .registers 21
    .param p1, "gl"    # Ljavax/microedition/khronos/opengles/GL10;
    .param p2, "width"    # I
    .param p3, "height"    # I

    .prologue
    .line 835
    const-string v2, "CircleUnlockRippleRenderer"

    const-string v3, "onSurfaceChanged"

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 836
    const-string v2, "CircleUnlockRippleRenderer"

    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "windowWidth = "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    move-object/from16 v0, p0

    iget v4, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v4, ", windowHeight = "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    move-object/from16 v0, p0

    iget v4, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v4, ", width = "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    move/from16 v0, p2

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v4, ", height = "

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    move/from16 v0, p3

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 838
    const/4 v2, 0x1

    move-object/from16 v0, p0

    iput-boolean v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isSurfaceChanged:Z

    .line 839
    const/4 v2, 0x0

    move-object/from16 v0, p0

    iput v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->drawCount:I

    .line 841
    move-object/from16 v0, p0

    iget-boolean v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    if-nez v2, :cond_5f

    move-object/from16 v0, p0

    iget-boolean v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    if-eqz v2, :cond_65

    .line 842
    :cond_5f
    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    const/4 v3, 0x1

    invoke-virtual {v2, v3}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    .line 844
    :cond_65
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isPrevSurfaceWidth:I

    move/from16 v0, p2

    if-ne v2, v0, :cond_75

    .line 846
    const-string v2, "CircleUnlockRippleRenderer"

    const-string v3, "isPrevSurfaceWidth == width"

    invoke-static {v2, v3}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 944
    :cond_74
    :goto_74
    return-void

    .line 850
    :cond_75
    move/from16 v0, p2

    move-object/from16 v1, p0

    iput v0, v1, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isPrevSurfaceWidth:I

    .line 852
    move/from16 v0, p2

    move/from16 v1, p3

    if-le v0, v1, :cond_19d

    .line 854
    const/4 v2, 0x1

    move-object/from16 v0, p0

    iput-boolean v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLandscape:Z

    .line 861
    :goto_86
    move-object/from16 v0, p0

    iget-boolean v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLandscape:Z

    if-eqz v2, :cond_1a4

    .line 864
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForLandscape:F

    move-object/from16 v0, p0

    iput v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForRipple:F

    .line 865
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    invoke-static {v2, v3}, Ljava/lang/Math;->max(II)I

    move-result v2

    move-object/from16 v0, p0

    iput v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    .line 866
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    invoke-static {v2, v3}, Ljava/lang/Math;->min(II)I

    move-result v2

    move-object/from16 v0, p0

    iput v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    .line 876
    :goto_b4
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    int-to-float v2, v2

    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    int-to-float v3, v3

    div-float v13, v2, v3

    .line 878
    .local v13, "ratio":F
    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->view:[F

    const/4 v3, 0x0

    const/4 v4, 0x0

    const/4 v5, 0x0

    const/high16 v6, 0x3f800000    # 1.0f

    const/4 v7, 0x0

    const/4 v8, 0x0

    const/4 v9, 0x0

    const/4 v10, 0x0

    const/high16 v11, 0x3f800000    # 1.0f

    const/4 v12, 0x0

    invoke-static/range {v2 .. v12}, Landroid/opengl/Matrix;->setLookAtM([FIFFFFFFFFF)V

    .line 879
    move-object/from16 v0, p0

    iget-object v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->proj:[F

    const/high16 v4, 0x42340000    # 45.0f

    const v6, 0x3dcccccd    # 0.1f

    const/high16 v7, 0x43fa0000    # 500.0f

    move-object/from16 v2, p0

    move v5, v13

    invoke-direct/range {v2 .. v7}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->perspectiveM([FFFFF)V

    .line 881
    const/4 v14, 0x0

    .line 882
    .local v14, "translateX":F
    const/4 v15, 0x0

    .line 883
    .local v15, "translateY":F
    const/16 v16, 0x0

    .line 885
    .local v16, "translateZ":F
    move-object/from16 v0, p0

    iget-boolean v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLandscape:Z

    if-eqz v2, :cond_1ce

    .line 887
    move-object/from16 v0, p0

    iget v14, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForLandscape:F

    .line 888
    move-object/from16 v0, p0

    iget v15, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForLandscape:F

    .line 889
    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForLandscape:F

    move/from16 v16, v0

    .line 898
    :goto_fc
    move-object/from16 v0, p0

    iput v14, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateX:F

    .line 899
    move-object/from16 v0, p0

    iput v15, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateY:F

    .line 900
    move/from16 v0, v16

    move-object/from16 v1, p0

    iput v0, v1, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateZ:F

    .line 901
    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->world:[F

    const/4 v3, 0x0

    invoke-static {v2, v3}, Landroid/opengl/Matrix;->setIdentityM([FI)V

    .line 902
    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    const/4 v3, 0x0

    move-object/from16 v0, p0

    iget-object v4, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->view:[F

    const/4 v5, 0x0

    move-object/from16 v0, p0

    iget-object v6, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->world:[F

    const/4 v7, 0x0

    invoke-static/range {v2 .. v7}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 903
    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    const/4 v3, 0x0

    move/from16 v0, v16

    invoke-static {v2, v3, v14, v15, v0}, Landroid/opengl/Matrix;->translateM([FIFFF)V

    .line 905
    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v3, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v2, v3, :cond_15f

    .line 907
    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    const/4 v3, 0x0

    const v4, 0x3f83d70a    # 1.03f

    const v5, 0x3f83d70a    # 1.03f

    const v6, 0x3f83d70a    # 1.03f

    invoke-static {v2, v3, v4, v5, v6}, Landroid/opengl/Matrix;->scaleM([FIFFF)V

    .line 908
    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    const/4 v3, 0x0

    invoke-static {v2, v3}, Landroid/opengl/Matrix;->setIdentityM([FI)V

    .line 909
    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    const/4 v3, 0x0

    move-object/from16 v0, p0

    iget-object v4, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    const/4 v5, 0x0

    move-object/from16 v0, p0

    iget-object v6, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    const/4 v7, 0x0

    invoke-static/range {v2 .. v7}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 911
    :cond_15f
    move-object/from16 v0, p0

    iget-object v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    const/4 v3, 0x0

    move-object/from16 v0, p0

    iget-object v4, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->proj:[F

    const/4 v5, 0x0

    move-object/from16 v0, p0

    iget-object v6, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    const/4 v7, 0x0

    invoke-static/range {v2 .. v7}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 913
    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v3, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v2, v3, :cond_1de

    .line 914
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    const/4 v4, 0x0

    invoke-static {v2, v3, v4}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onInitSetting(IIZ)V

    .line 915
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onInitGPU()V

    .line 931
    :cond_186
    :goto_186
    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v3, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v2, v3, :cond_21f

    .line 932
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    const/4 v4, 0x0

    invoke-static {v2, v3, v4}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onInitSetting(IIZ)V

    .line 933
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onInitGPU()V

    goto/16 :goto_74

    .line 858
    .end local v13    # "ratio":F
    .end local v14    # "translateX":F
    .end local v15    # "translateY":F
    .end local v16    # "translateZ":F
    :cond_19d
    const/4 v2, 0x0

    move-object/from16 v0, p0

    iput-boolean v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLandscape:Z

    goto/16 :goto_86

    .line 870
    :cond_1a4
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForPortrait:F

    move-object/from16 v0, p0

    iput v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForRipple:F

    .line 871
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    invoke-static {v2, v3}, Ljava/lang/Math;->min(II)I

    move-result v2

    move-object/from16 v0, p0

    iput v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    .line 872
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    invoke-static {v2, v3}, Ljava/lang/Math;->max(II)I

    move-result v2

    move-object/from16 v0, p0

    iput v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    goto/16 :goto_b4

    .line 893
    .restart local v13    # "ratio":F
    .restart local v14    # "translateX":F
    .restart local v15    # "translateY":F
    .restart local v16    # "translateZ":F
    :cond_1ce
    move-object/from16 v0, p0

    iget v14, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateXForPortrait:F

    .line 894
    move-object/from16 v0, p0

    iget v15, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateYForPortrait:F

    .line 895
    move-object/from16 v0, p0

    iget v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->translateZForPortrait:F

    move/from16 v16, v0

    goto/16 :goto_fc

    .line 917
    :cond_1de
    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v3, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v2, v3, :cond_1f4

    .line 918
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    const/4 v4, 0x1

    invoke-static {v2, v3, v4}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onInitSetting(IIZ)V

    .line 919
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onInitGPU()V

    goto :goto_186

    .line 921
    :cond_1f4
    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v3, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v2, v3, :cond_20e

    .line 922
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    const/4 v4, 0x1

    invoke-static {v2, v3, v4}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onInitSetting(IIZ)V

    .line 923
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onInitGPUGravity()V

    .line 924
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onLoadGravityTextures()V

    goto/16 :goto_186

    .line 926
    :cond_20e
    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v3, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-eq v2, v3, :cond_21a

    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v3, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v2, v3, :cond_186

    .line 928
    :cond_21a
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->clearInkValue()V

    goto/16 :goto_186

    .line 935
    :cond_21f
    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v3, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_INK:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v2, v3, :cond_236

    .line 936
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    const/4 v4, 0x1

    invoke-static {v2, v3, v4}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onInitSetting(IIZ)V

    .line 937
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onInitGPU()V

    goto/16 :goto_74

    .line 939
    :cond_236
    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v3, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v2, v3, :cond_74

    .line 940
    move-object/from16 v0, p0

    iget v2, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenWidth:I

    move-object/from16 v0, p0

    iget v3, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mScreenHeight:I

    const/4 v4, 0x1

    invoke-static {v2, v3, v4}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onInitSetting(IIZ)V

    .line 941
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onInitGPUGravity()V

    .line 942
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onLoadGravityTextures()V

    goto/16 :goto_74
.end method

.method public onSurfaceCreated(Ljavax/microedition/khronos/opengles/GL10;Ljavax/microedition/khronos/egl/EGLConfig;)V
    .registers 5
    .param p1, "gl"    # Ljavax/microedition/khronos/opengles/GL10;
    .param p2, "config"    # Ljavax/microedition/khronos/egl/EGLConfig;

    .prologue
    .line 818
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "onSurfaceCreated"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 820
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->loadBitmapIfBitmapNull()V

    .line 822
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onLoadBGTextures()V

    .line 823
    invoke-static {}, Lcom/android/internal/policy/impl/keyguard/sec/JniWaterRippleRender;->onLoadWaterTextures()V

    .line 825
    const/4 v0, 0x0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isPrevSurfaceWidth:I

    .line 827
    return-void
.end method

.method public releaseBooster(I)V
    .registers 5
    .param p1, "type"    # I

    .prologue
    const/4 v2, 0x0

    .line 2868
    if-nez p1, :cond_29

    .line 2870
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "== DVFS == releaseBooster CPU_CLOK_CONTROL"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2872
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedCPUClockTable:[I

    if-eqz v0, :cond_21

    .line 2874
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    if-eqz v0, :cond_1e

    .line 2876
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "== DVFS == cpu MaxClock Booster.release()!!!"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2877
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    invoke-virtual {v0}, Landroid/os/DVFSHelper;->release()V

    .line 2880
    :cond_1e
    iput-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedCPUClockTable:[I

    .line 2910
    :goto_20
    return-void

    .line 2884
    :cond_21
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "== DVFS == There are nothting aquire! CPU Clock Table is null"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_20

    .line 2887
    :cond_29
    const/4 v0, 0x1

    if-ne p1, v0, :cond_52

    .line 2889
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "== DVFS == releaseBooster GPU_FREQ_CONTROL"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2891
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedGPUFreqTable:[I

    if-eqz v0, :cond_4a

    .line 2893
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    if-eqz v0, :cond_47

    .line 2895
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "== DVFS == gpu MaxFreq Booster.release()!!!"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2896
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    invoke-virtual {v0}, Landroid/os/DVFSHelper;->release()V

    .line 2899
    :cond_47
    iput-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->supportedGPUFreqTable:[I

    goto :goto_20

    .line 2903
    :cond_4a
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "== DVFS == There are nothting aquire! GPU Freq Table is null"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_20

    .line 2908
    :cond_52
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "== DVFS == type is invalid."

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_20
.end method

.method public reset()V
    .registers 4

    .prologue
    const/4 v2, 0x0

    .line 2162
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "reset"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2164
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->clearAllEffect()V

    .line 2165
    iput-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isTouched:Z

    .line 2166
    iput-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isScreenTurnedOn:Z

    .line 2168
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->removeDefaultRunnable()V

    .line 2169
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setFalseDefaultEffectFlag()V

    .line 2171
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    if-nez v0, :cond_1d

    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_23

    .line 2172
    :cond_1d
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    const/4 v1, 0x1

    invoke-virtual {v0, v1}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    .line 2174
    :cond_23
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v0, v1, :cond_2e

    .line 2175
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSContextManager:Landroid/hardware/scontext/SContextManager;

    invoke-virtual {v0, p0}, Landroid/hardware/scontext/SContextManager;->unregisterListener(Landroid/hardware/scontext/SContextListener;)V

    .line 2177
    :cond_2e
    return-void
.end method

.method public screenTurnedOn()V
    .registers 5

    .prologue
    const/4 v3, 0x1

    .line 2088
    const-string v1, "CircleUnlockRippleRenderer"

    const-string v2, "screenTurnedOn"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2091
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->removeDefaultRunnable()V

    .line 2092
    iput-boolean v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->calledScreenTurnedOn:Z

    .line 2093
    iput-boolean v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isScreenTurnedOn:Z

    .line 2095
    const-string v1, "CircleUnlockRippleRenderer"

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "keyguardManager.isKeyguardLocked() = "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->keyguardManager:Landroid/app/KeyguardManager;

    invoke-virtual {v3}, Landroid/app/KeyguardManager;->isKeyguardLocked()Z

    move-result v3

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2097
    sget-object v1, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def;->MODE:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    sget-object v2, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;->RIPPLE_LIGHT_WITH_GRAVITY:Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Def$ModeType;

    if-ne v1, v2, :cond_50

    .line 2098
    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mContext:Landroid/content/Context;

    invoke-virtual {v1}, Landroid/content/Context;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v0

    .line 2099
    .local v0, "pm":Landroid/content/pm/PackageManager;
    const-string v1, "com.sec.feature.sensorhub"

    invoke-virtual {v0, v1}, Landroid/content/pm/PackageManager;->getSystemFeatureLevel(Ljava/lang/String;)I

    move-result v1

    const/4 v2, 0x5

    if-ne v1, v2, :cond_50

    .line 2100
    const-string v1, "CircleUnlockRippleRenderer"

    const-string v2, "register SContext"

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2101
    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSContextManager:Landroid/hardware/scontext/SContextManager;

    const/16 v2, 0x12

    invoke-virtual {v1, p0, v2}, Landroid/hardware/scontext/SContextManager;->registerListener(Landroid/hardware/scontext/SContextListener;I)Z

    .line 2105
    .end local v0    # "pm":Landroid/content/pm/PackageManager;
    :cond_50
    return-void
.end method

.method public setGravity(I)V
    .registers 2
    .param p1, "_v"    # I

    .prologue
    .line 2262
    iput p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gravityEffectType:I

    return-void
.end method

.method public setGravityTexture(Landroid/graphics/Bitmap;Landroid/graphics/Bitmap;Landroid/graphics/Bitmap;)V
    .registers 6
    .param p1, "pGravityBitmap"    # Landroid/graphics/Bitmap;
    .param p2, "pCausticsBitmap"    # Landroid/graphics/Bitmap;
    .param p3, "pCausticsBitmap2"    # Landroid/graphics/Bitmap;

    .prologue
    const/4 v1, 0x0

    .line 1703
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapGravity:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_c

    .line 1705
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapGravity:Landroid/graphics/Bitmap;

    invoke-virtual {v0}, Landroid/graphics/Bitmap;->recycle()V

    .line 1706
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapGravity:Landroid/graphics/Bitmap;

    .line 1709
    :cond_c
    iput-object p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapGravity:Landroid/graphics/Bitmap;

    .line 1711
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_19

    .line 1713
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics:Landroid/graphics/Bitmap;

    invoke-virtual {v0}, Landroid/graphics/Bitmap;->recycle()V

    .line 1714
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics:Landroid/graphics/Bitmap;

    .line 1716
    :cond_19
    iput-object p2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics:Landroid/graphics/Bitmap;

    .line 1718
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics2:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_26

    .line 1720
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics2:Landroid/graphics/Bitmap;

    invoke-virtual {v0}, Landroid/graphics/Bitmap;->recycle()V

    .line 1721
    iput-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics2:Landroid/graphics/Bitmap;

    .line 1723
    :cond_26
    iput-object p3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapCaustics2:Landroid/graphics/Bitmap;

    .line 1724
    return-void
.end method

.method public setRippleBackground()V
    .registers 3

    .prologue
    .line 2022
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "setRippleBackground()"

    invoke-static {v0, v1}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I

    .line 2023
    const/4 v0, 0x0

    invoke-direct {p0, v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setBackground(Z)V

    .line 2024
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBgChangeCheckArray:Ljava/util/ArrayList;

    const/4 v1, 0x1

    invoke-static {v1}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;

    move-result-object v1

    invoke-virtual {v0, v1}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 2025
    return-void
.end method

.method public setSoundNum(I)V
    .registers 2
    .param p1, "value"    # I

    .prologue
    .line 1397
    iput p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->soundNum:I

    .line 1398
    return-void
.end method

.method public setSoundRID(II)V
    .registers 3
    .param p1, "RDownId"    # I
    .param p2, "RUpId"    # I

    .prologue
    .line 1728
    iput p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRDownId:I

    .line 1729
    iput p2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRUpId:I

    .line 1730
    return-void
.end method

.method public setSoundTime(I)V
    .registers 2
    .param p1, "value"    # I

    .prologue
    .line 1401
    iput p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->soundTime:I

    .line 1402
    return-void
.end method

.method public setTexture(Landroid/graphics/Bitmap;)V
    .registers 5
    .param p1, "pBitmap"    # Landroid/graphics/Bitmap;

    .prologue
    .line 1669
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_c

    .line 1671
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    invoke-virtual {v0}, Landroid/graphics/Bitmap;->recycle()V

    .line 1672
    const/4 v0, 0x0

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    .line 1675
    :cond_c
    iput-object p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    .line 1677
    const-string v0, "CircleUnlockRippleRenderer"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "bitmapBG.width = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    invoke-virtual {v2}, Landroid/graphics/Bitmap;->getWidth()I

    move-result v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v1

    const-string v2, ", bitmapBG.height = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    invoke-virtual {v2}, Landroid/graphics/Bitmap;->getHeight()I

    move-result v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 1679
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    invoke-virtual {v0}, Landroid/graphics/Bitmap;->getHeight()I

    move-result v0

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapBG:Landroid/graphics/Bitmap;

    invoke-virtual {v1}, Landroid/graphics/Bitmap;->getWidth()I

    move-result v1

    if-ne v0, v1, :cond_4f

    .line 1681
    const/high16 v0, 0x3f800000    # 1.0f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBitmapRatio:F

    .line 1687
    :goto_4e
    return-void

    .line 1685
    :cond_4f
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    invoke-static {v0, v1}, Ljava/lang/Math;->max(II)I

    move-result v0

    int-to-float v0, v0

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowWidth:I

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->windowHeight:I

    invoke-static {v1, v2}, Ljava/lang/Math;->min(II)I

    move-result v1

    int-to-float v1, v1

    div-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBitmapRatio:F

    goto :goto_4e
.end method

.method public setWaterTexture(Landroid/graphics/Bitmap;)V
    .registers 3
    .param p1, "pBitmap"    # Landroid/graphics/Bitmap;

    .prologue
    .line 1691
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapWater:Landroid/graphics/Bitmap;

    if-eqz v0, :cond_c

    .line 1693
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapWater:Landroid/graphics/Bitmap;

    invoke-virtual {v0}, Landroid/graphics/Bitmap;->recycle()V

    .line 1694
    const/4 v0, 0x0

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapWater:Landroid/graphics/Bitmap;

    .line 1697
    :cond_c
    iput-object p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->bitmapWater:Landroid/graphics/Bitmap;

    .line 1699
    return-void
.end method

.method public show()V
    .registers 4

    .prologue
    const/4 v2, 0x1

    .line 2063
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "show"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2065
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setSound()V

    .line 2066
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setSound_gravity()V

    .line 2067
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->checkSound()V

    .line 2068
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setRippleVersion()V

    .line 2071
    iput-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isFirstTouched:Z

    .line 2072
    iput-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isShowCalled:Z

    .line 2073
    const/4 v0, 0x0

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isOrientationChanged:Z

    .line 2074
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setFalseDefaultEffectFlag()V

    .line 2076
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictCPUClock:Z

    if-eqz v0, :cond_28

    .line 2078
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    const/4 v1, 0x2

    invoke-virtual {v0, v1}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    .line 2081
    :cond_28
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->isRestrictGPUFreq:Z

    if-eqz v0, :cond_32

    .line 2083
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDVFSHandlerRipple:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;

    const/4 v1, 0x3

    invoke-virtual {v0, v1}, Landroid/os/Handler;->sendEmptyMessage(I)Z

    .line 2085
    :cond_32
    return-void
.end method

.method public showUnlockAffordance(JLandroid/graphics/Rect;)V
    .registers 7
    .param p1, "startDelay"    # J
    .param p3, "rect"    # Landroid/graphics/Rect;

    .prologue
    .line 2182
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "showUnlockAffordance()"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2183
    const-string v0, "CircleUnlockRippleRenderer"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "calledScreenTurnedOn = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->calledScreenTurnedOn:Z

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2184
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->removeDefaultRunnable()V

    .line 2186
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->calledScreenTurnedOn:Z

    if-eqz v0, :cond_62

    .line 2188
    iget v0, p3, Landroid/graphics/Rect;->left:I

    iget v1, p3, Landroid/graphics/Rect;->right:I

    iget v2, p3, Landroid/graphics/Rect;->left:I

    sub-int/2addr v1, v2

    div-int/lit8 v1, v1, 0x2

    add-int/2addr v0, v1

    int-to-float v0, v0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->defaultX:F

    .line 2189
    iget v0, p3, Landroid/graphics/Rect;->top:I

    iget v1, p3, Landroid/graphics/Rect;->bottom:I

    iget v2, p3, Landroid/graphics/Rect;->top:I

    sub-int/2addr v1, v2

    div-int/lit8 v1, v1, 0x2

    add-int/2addr v0, v1

    int-to-float v0, v0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->defaultY:F

    .line 2191
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDefaultRunnable:Ljava/lang/Runnable;

    if-nez v0, :cond_54

    .line 2193
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "mDefaultRunnable,  new Runnable()!!!"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2195
    new-instance v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;

    invoke-direct {v0, p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;-><init>(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)V

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDefaultRunnable:Ljava/lang/Runnable;

    .line 2222
    :cond_54
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "mDefaultRunnable, postDelayed()!!!"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2223
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mParent:Landroid/view/View;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDefaultRunnable:Ljava/lang/Runnable;

    invoke-virtual {v0, v1, p1, p2}, Landroid/view/View;->postDelayed(Ljava/lang/Runnable;J)Z

    .line 2225
    :cond_62
    return-void
.end method

.method public startLongPressCheck(Landroid/view/View;FFJF)V
    .registers 8
    .param p1, "view"    # Landroid/view/View;
    .param p2, "mouseX"    # F
    .param p3, "mouseY"    # F
    .param p4, "delay"    # J
    .param p6, "pIntensity"    # F

    .prologue
    .line 1317
    iput p2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->tmx:F

    .line 1318
    iput p3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->tmy:F

    .line 1320
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLongPressRunnable:Ljava/lang/Runnable;

    if-nez v0, :cond_f

    .line 1322
    new-instance v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$1;

    invoke-direct {v0, p0, p6}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$1;-><init>(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;F)V

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLongPressRunnable:Ljava/lang/Runnable;

    .line 1333
    :cond_f
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLongPressRunnable:Ljava/lang/Runnable;

    invoke-virtual {p1, v0, p4, p5}, Landroid/view/View;->postDelayed(Ljava/lang/Runnable;J)Z

    .line 1334
    return-void
.end method

.method public startLongPressCheck2(Landroid/view/View;FFJF)V
    .registers 8
    .param p1, "view"    # Landroid/view/View;
    .param p2, "mouseX"    # F
    .param p3, "mouseY"    # F
    .param p4, "delay"    # J
    .param p6, "pIntensity"    # F

    .prologue
    .line 1338
    iput p2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->tmx:F

    .line 1339
    iput p3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->tmy:F

    .line 1341
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLongPressRunnable:Ljava/lang/Runnable;

    if-nez v0, :cond_f

    .line 1343
    new-instance v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$2;

    invoke-direct {v0, p0, p6, p4, p5}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$2;-><init>(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;FJ)V

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLongPressRunnable:Ljava/lang/Runnable;

    .line 1357
    :cond_f
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLongPressRunnable:Ljava/lang/Runnable;

    invoke-virtual {p1, v0, p4, p5}, Landroid/view/View;->postDelayed(Ljava/lang/Runnable;J)Z

    .line 1358
    return-void
.end method

.method public stopLongPressCheck(Landroid/view/View;)V
    .registers 3
    .param p1, "view"    # Landroid/view/View;

    .prologue
    .line 1361
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mLongPressRunnable:Ljava/lang/Runnable;

    invoke-virtual {p1, v0}, Landroid/view/View;->removeCallbacks(Ljava/lang/Runnable;)Z

    .line 1362
    return-void
.end method

.method public unbindLeftDirectionEffect()V
    .registers 2

    .prologue
    .line 2475
    const/4 v0, 0x3

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    .line 2476
    const v0, 0x3ecccccd    # 0.4f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    .line 2477
    const/4 v0, 0x0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDrawEffectFrameCnt:I

    .line 2479
    const/4 v0, 0x1

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    .line 2480
    const v0, 0x3f6a3d71    # 0.915f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveReductionRate:F

    .line 2481
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->onMakeLeftDirectionEndRipple()V

    .line 2483
    return-void
.end method

.method public unbindRightDirectionEffect()V
    .registers 3

    .prologue
    const/4 v1, 0x1

    .line 2358
    iput v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    .line 2359
    const v0, 0x3ecccccd    # 0.4f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    .line 2360
    const/4 v0, 0x0

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mDrawEffectFrameCnt:I

    .line 2361
    iput-boolean v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    .line 2363
    const v0, 0x3f6a3d71    # 0.915f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mBottomWaveReductionRate:F

    .line 2364
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->onMakeRightDirectionEndRipple()V

    .line 2365
    return-void
.end method

.method public updateBGTiltAnimation()V
    .registers 11

    .prologue
    const/4 v9, 0x1

    const v8, 0x3f83d70a    # 1.03f

    const/high16 v7, 0x3f800000    # 1.0f

    const/4 v6, 0x0

    const/4 v1, 0x0

    .line 2281
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    const/4 v2, -0x1

    if-eq v0, v2, :cond_163

    .line 2283
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    if-nez v0, :cond_66

    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    if-ne v0, v9, :cond_66

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    const v2, 0x3e99999a    # 0.3f

    cmpg-float v0, v0, v2

    if-gtz v0, :cond_66

    .line 2285
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->world:[F

    invoke-static {v0, v1}, Landroid/opengl/Matrix;->setIdentityM([FI)V

    .line 2286
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->view:[F

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->world:[F

    move v3, v1

    move v5, v1

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 2287
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateX:F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateY:F

    iget v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateZ:F

    invoke-static {v0, v1, v2, v3, v4}, Landroid/opengl/Matrix;->translateM([FIFFF)V

    .line 2288
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    const/high16 v3, 0x41a00000    # 20.0f

    mul-float/2addr v2, v3

    move v3, v6

    move v4, v7

    move v5, v6

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->rotateM([FIFFFF)V

    .line 2290
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    invoke-static {v0, v1, v8, v8, v7}, Landroid/opengl/Matrix;->scaleM([FIFFF)V

    .line 2291
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    invoke-static {v0, v1}, Landroid/opengl/Matrix;->setIdentityM([FI)V

    .line 2292
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    move v3, v1

    move v5, v1

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 2293
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->proj:[F

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    move v3, v1

    move v5, v1

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 2296
    :cond_66
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    if-ne v0, v9, :cond_b6

    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    if-ne v0, v9, :cond_b6

    .line 2298
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->world:[F

    invoke-static {v0, v1}, Landroid/opengl/Matrix;->setIdentityM([FI)V

    .line 2299
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->view:[F

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->world:[F

    move v3, v1

    move v5, v1

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 2300
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateX:F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateY:F

    iget v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateZ:F

    invoke-static {v0, v1, v2, v3, v4}, Landroid/opengl/Matrix;->translateM([FIFFF)V

    .line 2301
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    const/high16 v3, 0x41700000    # 15.0f

    mul-float/2addr v2, v3

    move v3, v6

    move v4, v7

    move v5, v6

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->rotateM([FIFFFF)V

    .line 2302
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    invoke-static {v0, v1, v8, v8, v7}, Landroid/opengl/Matrix;->scaleM([FIFFF)V

    .line 2303
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    invoke-static {v0, v1}, Landroid/opengl/Matrix;->setIdentityM([FI)V

    .line 2304
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    move v3, v1

    move v5, v1

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 2305
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->proj:[F

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    move v3, v1

    move v5, v1

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 2308
    :cond_b6
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    const/4 v2, 0x2

    if-ne v0, v2, :cond_111

    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    if-ne v0, v9, :cond_111

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    const v2, 0x3e99999a    # 0.3f

    cmpg-float v0, v0, v2

    if-gtz v0, :cond_111

    .line 2310
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->world:[F

    invoke-static {v0, v1}, Landroid/opengl/Matrix;->setIdentityM([FI)V

    .line 2311
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->view:[F

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->world:[F

    move v3, v1

    move v5, v1

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 2312
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateX:F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateY:F

    iget v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateZ:F

    invoke-static {v0, v1, v2, v3, v4}, Landroid/opengl/Matrix;->translateM([FIFFF)V

    .line 2313
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    neg-float v2, v2

    const/high16 v3, 0x41a00000    # 20.0f

    mul-float/2addr v2, v3

    move v3, v6

    move v4, v7

    move v5, v6

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->rotateM([FIFFFF)V

    .line 2314
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    invoke-static {v0, v1, v8, v8, v7}, Landroid/opengl/Matrix;->scaleM([FIFFF)V

    .line 2315
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    invoke-static {v0, v1}, Landroid/opengl/Matrix;->setIdentityM([FI)V

    .line 2316
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    move v3, v1

    move v5, v1

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 2317
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->proj:[F

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    move v3, v1

    move v5, v1

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 2319
    :cond_111
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    const/4 v2, 0x3

    if-ne v0, v2, :cond_163

    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    if-ne v0, v9, :cond_163

    .line 2321
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->world:[F

    invoke-static {v0, v1}, Landroid/opengl/Matrix;->setIdentityM([FI)V

    .line 2322
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->view:[F

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->world:[F

    move v3, v1

    move v5, v1

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 2323
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateX:F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateY:F

    iget v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mTranslateZ:F

    invoke-static {v0, v1, v2, v3, v4}, Landroid/opengl/Matrix;->translateM([FIFFF)V

    .line 2324
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    neg-float v2, v2

    const/high16 v3, 0x41700000    # 15.0f

    mul-float/2addr v2, v3

    move v3, v6

    move v4, v7

    move v5, v6

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->rotateM([FIFFFF)V

    .line 2325
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    invoke-static {v0, v1, v8, v8, v7}, Landroid/opengl/Matrix;->scaleM([FIFFF)V

    .line 2326
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    invoke-static {v0, v1}, Landroid/opengl/Matrix;->setIdentityM([FI)V

    .line 2327
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wv:[F

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    move v3, v1

    move v5, v1

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 2328
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->proj:[F

    iget-object v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->wvp:[F

    move v3, v1

    move v5, v1

    invoke-static/range {v0 .. v5}, Landroid/opengl/Matrix;->multiplyMM([FI[FI[FI)V

    .line 2333
    :cond_163
    return-void
.end method

.method public updateCausticsMixRatio()V
    .registers 6

    .prologue
    const/4 v4, 0x0

    .line 2705
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeMix:F

    iget v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MarkcuasticsTMix:F

    add-float/2addr v0, v1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeMix:F

    .line 2706
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeMix:F

    const/high16 v1, 0x3f800000    # 1.0f

    cmpl-float v0, v0, v1

    if-lez v0, :cond_1c

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MarkcuasticsTMix:F

    cmpl-float v0, v0, v4

    if-lez v0, :cond_1c

    .line 2708
    const v0, -0x43dc28f6    # -0.01f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MarkcuasticsTMix:F

    .line 2718
    :cond_1b
    :goto_1b
    return-void

    .line 2710
    :cond_1c
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeMix:F

    cmpg-float v0, v0, v4

    if-gez v0, :cond_2e

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MarkcuasticsTMix:F

    cmpg-float v0, v0, v4

    if-gez v0, :cond_2e

    .line 2712
    const v0, 0x3c23d70a    # 0.01f

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->MarkcuasticsTMix:F

    goto :goto_1b

    .line 2714
    :cond_2e
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeMix:F

    float-to-double v0, v0

    const-wide v2, 0x3ff199999999999aL    # 1.1

    cmpl-double v0, v0, v2

    if-lez v0, :cond_1b

    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeMix:F

    float-to-double v0, v0

    const-wide v2, -0x4046666666666666L    # -0.1

    cmpg-double v0, v0, v2

    if-gez v0, :cond_1b

    .line 2716
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeMix:F

    goto :goto_1b
.end method

.method public updateGravityRippleEffect()V
    .registers 2

    .prologue
    .line 2265
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    packed-switch v0, :pswitch_data_e

    .line 2277
    :goto_5
    return-void

    .line 2269
    :pswitch_6
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->updateRightDirectionAnimation()V

    goto :goto_5

    .line 2274
    :pswitch_a
    invoke-virtual {p0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->updateLeftDirectionAnimation()V

    goto :goto_5

    .line 2265
    :pswitch_data_e
    .packed-switch 0x0
        :pswitch_6
        :pswitch_6
        :pswitch_a
        :pswitch_a
    .end packed-switch
.end method

.method public updateLeftDirectionAnimation()V
    .registers 9

    .prologue
    const/4 v7, 0x0

    const/high16 v5, 0x3f000000    # 0.5f

    const v6, 0x3c23d70a    # 0.01f

    const/4 v4, 0x0

    .line 2489
    iget-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    if-nez v2, :cond_c

    .line 2563
    :cond_b
    :goto_b
    return-void

    .line 2492
    :cond_c
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    const/4 v3, 0x2

    if-ne v2, v3, :cond_8b

    .line 2494
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    const v3, 0x3ecccccd    # 0.4f

    cmpg-float v2, v2, v3

    if-gtz v2, :cond_86

    .line 2496
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ReferencePoint:F

    const/high16 v3, 0x42480000    # 50.0f

    cmpg-float v2, v2, v3

    if-gtz v2, :cond_27

    .line 2497
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ReferencePoint:F

    add-float/2addr v2, v5

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ReferencePoint:F

    .line 2500
    :cond_27
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    const v3, 0x38d1b717    # 1.0E-4f

    sub-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    .line 2501
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    cmpg-float v2, v2, v4

    if-gez v2, :cond_37

    .line 2503
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    .line 2505
    :cond_37
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    invoke-virtual {p0, v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->getInterpolation80(F)F

    move-result v2

    mul-float v1, v2, v6

    .line 2506
    .local v1, "addDirection":F
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    add-float/2addr v2, v1

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    .line 2509
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TiltStartU:F

    add-float/2addr v2, v3

    sub-float v2, v5, v2

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TexMoveU:F

    .line 2510
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    const v3, 0x3f7ae148    # 0.98f

    mul-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 2518
    .end local v1    # "addDirection":F
    :goto_55
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    const v3, 0x3fb33333    # 1.4f

    cmpg-float v2, v2, v3

    if-gtz v2, :cond_63

    .line 2519
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    add-float/2addr v2, v6

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    .line 2522
    :cond_63
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    float-to-double v2, v2

    const-wide v4, 0x3fb999999999999aL    # 0.1

    cmpl-double v2, v2, v4

    if-ltz v2, :cond_74

    .line 2523
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    sub-float/2addr v2, v6

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 2526
    :cond_74
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    float-to-double v2, v2

    const-wide/high16 v4, 0x3fe0000000000000L    # 0.5

    cmpg-double v2, v2, v4

    if-gez v2, :cond_b

    .line 2527
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    const v3, 0x3dcccccd    # 0.1f

    add-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    goto :goto_b

    .line 2514
    :cond_86
    iput-boolean v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    .line 2515
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    goto :goto_55

    .line 2532
    :cond_8b
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    cmpl-float v2, v2, v4

    if-ltz v2, :cond_e3

    .line 2534
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    const v3, 0x3b449ba6    # 0.003f

    add-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    .line 2535
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    invoke-virtual {p0, v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->getInterpolation70(F)F

    move-result v0

    .line 2536
    .local v0, "addDiection":F
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    sub-float/2addr v2, v0

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    .line 2537
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    const v3, 0x3f75c28f    # 0.96f

    mul-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 2539
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->leftDirectionTilt:F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TiltStartU:F

    add-float/2addr v2, v3

    sub-float v2, v5, v2

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TexMoveU:F

    .line 2550
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    const/high16 v3, 0x3f800000    # 1.0f

    cmpl-float v2, v2, v3

    if-ltz v2, :cond_c5

    .line 2551
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    const v3, 0x3c75c28f    # 0.015f

    sub-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    .line 2554
    :cond_c5
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    cmpl-float v2, v2, v4

    if-ltz v2, :cond_d3

    .line 2555
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    const v3, 0x3ba3d70a    # 0.005f

    sub-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 2558
    :cond_d3
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    cmpl-float v2, v2, v4

    if-ltz v2, :cond_b

    .line 2559
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    const v3, 0x3ca3d70a    # 0.02f

    sub-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    goto/16 :goto_b

    .line 2544
    .end local v0    # "addDiection":F
    :cond_e3
    const/4 v2, -0x1

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    .line 2545
    iput-boolean v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    goto/16 :goto_b
.end method

.method public updateRightDirectionAnimation()V
    .registers 9

    .prologue
    const/4 v7, 0x0

    const/high16 v5, 0x3f000000    # 0.5f

    const v6, 0x3c23d70a    # 0.01f

    const/4 v4, 0x0

    .line 2372
    iget-boolean v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    if-nez v2, :cond_c

    .line 2449
    :cond_b
    :goto_b
    return-void

    .line 2375
    :cond_c
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    if-nez v2, :cond_82

    .line 2377
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    const v3, 0x3ecccccd    # 0.4f

    cmpg-float v2, v2, v3

    if-gtz v2, :cond_7d

    .line 2380
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ReferencePoint:F

    const/high16 v3, 0x42480000    # 50.0f

    cmpg-float v2, v2, v3

    if-gtz v2, :cond_26

    .line 2381
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ReferencePoint:F

    add-float/2addr v2, v5

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->ReferencePoint:F

    .line 2383
    :cond_26
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    const v3, 0x38d1b717    # 1.0E-4f

    sub-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    .line 2384
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    cmpg-float v2, v2, v4

    if-gez v2, :cond_36

    .line 2386
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    .line 2388
    :cond_36
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    invoke-virtual {p0, v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->getInterpolation80(F)F

    move-result v2

    mul-float v1, v2, v6

    .line 2390
    .local v1, "addDirection":F
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    add-float/2addr v2, v1

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    .line 2391
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TiltStartU:F

    add-float/2addr v2, v3

    sub-float v2, v5, v2

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TexMoveU:F

    .line 2400
    .end local v1    # "addDirection":F
    :goto_4c
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    const v3, 0x3fb33333    # 1.4f

    cmpg-float v2, v2, v3

    if-gtz v2, :cond_5a

    .line 2401
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    add-float/2addr v2, v6

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    .line 2405
    :cond_5a
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    float-to-double v2, v2

    const-wide v4, 0x3fb999999999999aL    # 0.1

    cmpl-double v2, v2, v4

    if-ltz v2, :cond_6b

    .line 2406
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    sub-float/2addr v2, v6

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 2408
    :cond_6b
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    float-to-double v2, v2

    const-wide/high16 v4, 0x3fe0000000000000L    # 0.5

    cmpg-double v2, v2, v4

    if-gez v2, :cond_b

    .line 2409
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    const v3, 0x3dcccccd    # 0.1f

    add-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    goto :goto_b

    .line 2396
    :cond_7d
    iput-boolean v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    .line 2397
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    goto :goto_4c

    .line 2417
    :cond_82
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    cmpl-float v2, v2, v4

    if-ltz v2, :cond_da

    .line 2420
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    const v3, 0x3b449ba6    # 0.003f

    add-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    .line 2421
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    invoke-virtual {p0, v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->getInterpolation70(F)F

    move-result v0

    .line 2422
    .local v0, "addDiection":F
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    sub-float/2addr v2, v0

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    .line 2423
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    const v3, 0x3f75c28f    # 0.96f

    mul-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 2425
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->rightDirectionTilt:F

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TiltStartU:F

    add-float/2addr v2, v3

    sub-float v2, v5, v2

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->TexMoveU:F

    .line 2435
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    const/high16 v3, 0x3f800000    # 1.0f

    cmpl-float v2, v2, v3

    if-ltz v2, :cond_bc

    .line 2436
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    const v3, 0x3c75c28f    # 0.015f

    sub-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->fWaterBrightness:F

    .line 2439
    :cond_bc
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    cmpl-float v2, v2, v4

    if-ltz v2, :cond_ca

    .line 2440
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    const v3, 0x3ba3d70a    # 0.005f

    sub-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio:F

    .line 2443
    :cond_ca
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    cmpl-float v2, v2, v4

    if-ltz v2, :cond_b

    .line 2444
    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    const v3, 0x3ca3d70a    # 0.02f

    sub-float/2addr v2, v3

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->causticsTimeRatio2:F

    goto/16 :goto_b

    .line 2429
    .end local v0    # "addDiection":F
    :cond_da
    const/4 v2, -0x1

    iput v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mSelectEffect:I

    .line 2430
    iput-boolean v7, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mRunDirectionAni:Z

    .line 2431
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->animationSpeed:F

    goto/16 :goto_b
.end method
