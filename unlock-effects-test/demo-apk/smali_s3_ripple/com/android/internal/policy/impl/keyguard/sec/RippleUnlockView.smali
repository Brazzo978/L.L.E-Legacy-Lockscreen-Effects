.class public Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;
.super Landroid/opengl/GLSurfaceView;
.source "RippleUnlockView.java"

# interfaces
.implements Lcom/android/internal/policy/impl/keyguard/sec/UnlockView;


# static fields
.field private static sRippleUnlockView:Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;


# instance fields
.field private final DBG:Z

.field private final TAG:Ljava/lang/String;

.field private isTablet:Z

.field keyguardManager:Landroid/app/KeyguardManager;

.field private mContext:Landroid/content/Context;

.field private mPowerManager:Landroid/os/PowerManager;

.field private mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

.field private prevOrientation:I

.field private windowHeight:I

.field private windowWidth:I


# direct methods
.method public constructor <init>(Landroid/content/Context;)V
    .registers 12
    .param p1, "context"    # Landroid/content/Context;

    .prologue
    const/4 v4, 0x2

    const/4 v3, 0x1

    const/16 v1, 0x8

    const/4 v6, 0x0

    .line 61
    invoke-direct {p0, p1}, Landroid/opengl/GLSurfaceView;-><init>(Landroid/content/Context;)V

    .line 42
    iput-boolean v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->DBG:Z

    .line 44
    const-string v0, "CircleUnlockRipple"

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->TAG:Ljava/lang/String;

    .line 55
    const/4 v0, -0x1

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->prevOrientation:I

    .line 56
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->windowWidth:I

    .line 57
    iput v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->windowHeight:I

    .line 58
    iput-boolean v6, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->isTablet:Z

    .line 62
    iput-object p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mContext:Landroid/content/Context;

    .line 64
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mContext:Landroid/content/Context;

    const-string v2, "keyguard"

    invoke-virtual {v0, v2}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/KeyguardManager;

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->keyguardManager:Landroid/app/KeyguardManager;

    .line 65
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mContext:Landroid/content/Context;

    const-string v2, "power"

    invoke-virtual {v0, v2}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/os/PowerManager;

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mPowerManager:Landroid/os/PowerManager;

    .line 67
    new-instance v8, Landroid/util/DisplayMetrics;

    invoke-direct {v8}, Landroid/util/DisplayMetrics;-><init>()V

    .line 68
    .local v8, "displayMetrics":Landroid/util/DisplayMetrics;
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mContext:Landroid/content/Context;

    const-string v2, "window"

    invoke-virtual {v0, v2}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v9

    check-cast v9, Landroid/view/WindowManager;

    .line 69
    .local v9, "mWindowManager":Landroid/view/WindowManager;
    invoke-interface {v9}, Landroid/view/WindowManager;->getDefaultDisplay()Landroid/view/Display;

    move-result-object v0

    invoke-virtual {v0, v8}, Landroid/view/Display;->getRealMetrics(Landroid/util/DisplayMetrics;)V

    .line 70
    iget v0, v8, Landroid/util/DisplayMetrics;->widthPixels:I

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->windowWidth:I

    .line 71
    iget v0, v8, Landroid/util/DisplayMetrics;->heightPixels:I

    iput v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->windowHeight:I

    .line 73
    const-string v0, "ro.build.characteristics"

    invoke-static {v0}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v7

    .line 75
    .local v7, "deviceType":Ljava/lang/String;
    if-eqz v7, :cond_5f

    .line 76
    const-string v0, "tablet"

    invoke-virtual {v7, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v0

    iput-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->isTablet:Z

    .line 79
    :cond_5f
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->isTablet:Z

    if-eqz v0, :cond_6b

    .line 81
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->windowWidth:I

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->windowHeight:I

    if-le v0, v2, :cond_a4

    .line 83
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->prevOrientation:I

    .line 94
    :cond_6b
    :goto_6b
    new-instance v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->windowWidth:I

    iget v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->windowHeight:I

    invoke-direct {v0, p1, p0, v2, v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;-><init>(Landroid/content/Context;Landroid/view/View;II)V

    iput-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    .line 95
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    sget v2, Lcom/codex/s4unlockfx/R$raw;->s3_ripple_down:I

    sget v3, Lcom/codex/s4unlockfx/R$raw;->s3_ripple_up:I

    invoke-virtual {v0, v2, v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setSoundRID(II)V

    .line 97
    invoke-direct {p0}, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->detectOpenGLES20()Z

    move-result v0

    if-eqz v0, :cond_a7

    .line 98
    invoke-virtual {p0, v4}, Landroid/opengl/GLSurfaceView;->setEGLContextClientVersion(I)V

    .line 99
    const/16 v5, 0x10

    move-object v0, p0

    move v2, v1

    move v3, v1

    move v4, v1

    invoke-virtual/range {v0 .. v6}, Landroid/opengl/GLSurfaceView;->setEGLConfigChooser(IIIIII)V

    .line 100
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {p0, v0}, Landroid/opengl/GLSurfaceView;->setRenderer(Landroid/opengl/GLSurfaceView$Renderer;)V

    .line 101
    invoke-virtual {p0, v6}, Landroid/opengl/GLSurfaceView;->setRenderMode(I)V

    .line 102
    invoke-virtual {p0}, Landroid/view/SurfaceView;->getHolder()Landroid/view/SurfaceHolder;

    move-result-object v0

    const/4 v1, 0x3

    invoke-interface {v0, v1}, Landroid/view/SurfaceHolder;->setFormat(I)V

    .line 108
    :goto_a3
    return-void

    .line 87
    :cond_a4
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->prevOrientation:I

    goto :goto_6b

    .line 106
    :cond_a7
    const-string v0, "WaterEffect"

    const-string v1, "this machine does not support OpenGL ES2.0"

    invoke-static {v0, v1}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_a3
.end method

.method private detectOpenGLES20()Z
    .registers 6

    .prologue
    const/4 v2, 0x0

    .line 121
    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mContext:Landroid/content/Context;

    const-string v4, "activity"

    invoke-virtual {v3, v4}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/ActivityManager;

    .line 122
    .local v0, "am":Landroid/app/ActivityManager;
    invoke-virtual {v0}, Landroid/app/ActivityManager;->getDeviceConfigurationInfo()Landroid/content/pm/ConfigurationInfo;

    move-result-object v1

    .line 123
    .local v1, "info":Landroid/content/pm/ConfigurationInfo;
    if-eqz v1, :cond_18

    .line 124
    iget v3, v1, Landroid/content/pm/ConfigurationInfo;->reqGlEsVersion:I

    const/high16 v4, 0x20000

    if-lt v3, v4, :cond_18

    const/4 v2, 0x1

    .line 126
    :cond_18
    return v2
.end method

.method public static getInstance()Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;
    .registers 1

    .prologue
    .line 117
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->sRippleUnlockView:Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;

    return-object v0
.end method

.method public static getInstance(Landroid/content/Context;)Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;
    .registers 2
    .param p0, "context"    # Landroid/content/Context;

    .prologue
    .line 111
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->sRippleUnlockView:Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;

    if-nez v0, :cond_b

    .line 112
    new-instance v0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;

    invoke-direct {v0, p0}, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;-><init>(Landroid/content/Context;)V

    sput-object v0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->sRippleUnlockView:Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;

    .line 113
    :cond_b
    sget-object v0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->sRippleUnlockView:Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;

    return-object v0
.end method


# virtual methods
.method public cleanUp()V
    .registers 3

    .prologue
    .line 153
    const-string v0, "CircleUnlockRipple"

    const-string v1, "cleanUp"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 154
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cleanUp()V

    .line 155
    return-void
.end method

.method public gatherTransparentRegion(Landroid/graphics/Region;)Z
    .registers 3
    .param p1, "region"    # Landroid/graphics/Region;

    .prologue
    .line 149
    const/4 v0, 0x0

    return v0
.end method

.method public getUnlockDelay()J
    .registers 3

    .prologue
    .line 209
    const-wide/16 v0, 0x0

    return-wide v0
.end method

.method public handleHoverEvent(Landroid/view/MotionEvent;)Z
    .registers 5
    .param p1, "event"    # Landroid/view/MotionEvent;

    .prologue
    .line 214
    const-string v0, "CircleUnlockRipple"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "handleHoverEvent : "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {p1}, Landroid/view/MotionEvent;->getActionMasked()I

    move-result v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 215
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    const/4 v1, 0x0

    invoke-virtual {v0, v1, p1}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseMove(Landroid/view/View;Landroid/view/MotionEvent;)Z

    .line 216
    const/4 v0, 0x0

    return v0
.end method

.method public handleTouchEvent(Landroid/view/View;Landroid/view/MotionEvent;)Z
    .registers 7
    .param p1, "view"    # Landroid/view/View;
    .param p2, "event"    # Landroid/view/MotionEvent;

    .prologue
    .line 140
    const-string v1, "CircleUnlockRipple"

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "handleTouchEvent event : "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {p2}, Landroid/view/MotionEvent;->getAction()I

    move-result v3

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;

    move-result-object v2

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v2

    invoke-static {v1, v2}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 141
    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v1, p1, p2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->mouseMove(Landroid/view/View;Landroid/view/MotionEvent;)Z

    move-result v0

    .line 142
    .local v0, "result":Z
    if-eqz v0, :cond_24

    .line 144
    :cond_24
    const/4 v1, 0x1

    return v1
.end method

.method public handleTouchEventForPatternLock(Landroid/view/View;Landroid/view/MotionEvent;)Z
    .registers 4
    .param p1, "view"    # Landroid/view/View;
    .param p2, "event"    # Landroid/view/MotionEvent;

    .prologue
    .line 231
    const/4 v0, 0x0

    return v0
.end method

.method public handleUnlock(Landroid/view/View;Landroid/view/MotionEvent;)V
    .registers 3
    .param p1, "view"    # Landroid/view/View;
    .param p2, "event"    # Landroid/view/MotionEvent;

    .prologue
    .line 177
    return-void
.end method

.method public onConfigurationChanged(Landroid/content/res/Configuration;)V
    .registers 7
    .param p1, "newConfig"    # Landroid/content/res/Configuration;

    .prologue
    const/4 v4, 0x2

    const/4 v3, 0x1

    .line 246
    invoke-super {p0, p1}, Landroid/view/View;->onConfigurationChanged(Landroid/content/res/Configuration;)V

    .line 248
    iget-boolean v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->isTablet:Z

    if-eqz v0, :cond_45

    .line 250
    const-string v0, "CircleUnlockRipple"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "keyguardManager.isKeyguardLocked() = "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->keyguardManager:Landroid/app/KeyguardManager;

    invoke-virtual {v2}, Landroid/app/KeyguardManager;->isKeyguardLocked()Z

    move-result v2

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 252
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->keyguardManager:Landroid/app/KeyguardManager;

    invoke-virtual {v0}, Landroid/app/KeyguardManager;->isKeyguardLocked()Z

    move-result v0

    if-eqz v0, :cond_45

    .line 254
    iget v0, p1, Landroid/content/res/Configuration;->orientation:I

    if-ne v0, v4, :cond_46

    .line 256
    const-string v0, "CircleUnlockRipple"

    const-string v1, "= onConfigurationChanged = ORIENTATION_LANDSCAPE"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 258
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->prevOrientation:I

    if-eq v0, v4, :cond_45

    .line 260
    iput v4, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->prevOrientation:I

    .line 261
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->onConfigurationChanged()V

    .line 276
    :cond_45
    :goto_45
    return-void

    .line 264
    :cond_46
    iget v0, p1, Landroid/content/res/Configuration;->orientation:I

    if-ne v0, v3, :cond_45

    .line 266
    const-string v0, "CircleUnlockRipple"

    const-string v1, "= onConfigurationChanged = ORIENTATION_PORTRAIT"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 268
    iget v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->prevOrientation:I

    if-eq v0, v3, :cond_45

    .line 270
    iput v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->prevOrientation:I

    .line 271
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->onConfigurationChanged()V

    goto :goto_45
.end method

.method protected onDetachedFromWindow()V
    .registers 3

    .prologue
    .line 238
    invoke-super {p0}, Landroid/opengl/GLSurfaceView;->onDetachedFromWindow()V

    .line 239
    const-string v0, "CircleUnlockRipple"

    const-string v1, "onDetachedFromWindow"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 240
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->destroyed()V

    .line 241
    return-void
.end method

.method public onPause()V
    .registers 1

    .prologue
    .line 159
    return-void
.end method

.method public onResume()V
    .registers 1

    .prologue
    .line 163
    return-void
.end method

.method protected onWindowVisibilityChanged(I)V
    .registers 2
    .param p1, "visibility"    # I

    .prologue
    .line 168
    if-nez p1, :cond_5

    .line 169
    invoke-super {p0, p1}, Landroid/view/SurfaceView;->onWindowVisibilityChanged(I)V

    .line 171
    :cond_5
    return-void
.end method

.method public playLockSound()V
    .registers 1

    .prologue
    .line 227
    return-void
.end method

.method public reset()V
    .registers 3

    .prologue
    .line 195
    const-string v0, "CircleUnlockRipple"

    const-string v1, "reset"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 196
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->reset()V

    .line 197
    const-string v0, "CircleUnlockRipple"

    const-string v1, "requestRender()"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 198
    invoke-virtual {p0}, Landroid/opengl/GLSurfaceView;->requestRender()V

    .line 199
    return-void
.end method

.method public screenTurnedOn()V
    .registers 3

    .prologue
    .line 203
    const-string v0, "CircleUnlockRipple"

    const-string v1, "screenTurnedOn"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 204
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->screenTurnedOn()V

    .line 205
    return-void
.end method

.method setBackground()V
    .registers 2

    .prologue
    .line 131
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setRippleBackground()V

    .line 133
    invoke-virtual {p0}, Landroid/opengl/GLSurfaceView;->requestRender()V

    .line 135
    return-void
.end method

.method public show()V
    .registers 3

    .prologue
    .line 182
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mContext:Landroid/content/Context;

    invoke-static {v0}, Lcom/android/internal/policy/impl/keyguard/sec/LockscreenWallpaper;->isFlipboardWallpaper(Landroid/content/Context;)Z

    move-result v0

    if-eqz v0, :cond_d

    .line 183
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->setRippleBackground()V

    .line 185
    :cond_d
    const-string v0, "CircleUnlockRipple"

    const-string v1, "show"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 186
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->show()V

    .line 189
    const-string v0, "CircleUnlockRipple"

    const-string v1, "requestRender()"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 190
    invoke-virtual {p0}, Landroid/opengl/GLSurfaceView;->requestRender()V

    .line 191
    return-void
.end method

.method public showUnlockAffordance(JLandroid/graphics/Rect;)V
    .registers 7
    .param p1, "startDelay"    # J
    .param p3, "rect"    # Landroid/graphics/Rect;

    .prologue
    .line 221
    const-string v0, "CircleUnlockRipple"

    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "showUnlockAffordance startDelay : "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, p1, p2}, Ljava/lang/StringBuilder;->append(J)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 222
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/RippleUnlockView;->mRenderer:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0, p1, p2, p3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->showUnlockAffordance(JLandroid/graphics/Rect;)V

    .line 223
    return-void
.end method
