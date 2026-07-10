.class Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;
.super Ljava/lang/Object;
.source "CircleUnlockRippleRenderer.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->showUnlockAffordance(JLandroid/graphics/Rect;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;


# direct methods
.method constructor <init>(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)V
    .registers 2

    .prologue
    .line 2196
    iput-object p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .registers 6

    .prologue
    const/high16 v4, 0x40000000    # 2.0f

    .line 2199
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$900(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)Z

    move-result v0

    if-eqz v0, :cond_96

    .line 2201
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v1}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1000(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)F

    move-result v1

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1100(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I

    move-result v2

    int-to-float v2, v2

    div-float/2addr v2, v4

    sub-float/2addr v1, v2

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v2, v2, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustLandscape:F

    sub-float/2addr v1, v2

    iput v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    .line 2202
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v1, v1, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v2, v2, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForLandscape:F

    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1100(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I

    move-result v3

    int-to-float v3, v3

    div-float/2addr v2, v3

    mul-float/2addr v1, v2

    iput v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    .line 2203
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v1}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1200(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I

    move-result v1

    int-to-float v1, v1

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1300(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)F

    move-result v2

    sub-float/2addr v1, v2

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1200(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I

    move-result v2

    int-to-float v2, v2

    div-float/2addr v2, v4

    sub-float/2addr v1, v2

    iput v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    .line 2204
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v1, v1, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    neg-float v1, v1

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v2, v2, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForLandscape:F

    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1200(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I

    move-result v3

    int-to-float v3, v3

    div-float/2addr v2, v3

    mul-float/2addr v1, v2

    iput v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    .line 2213
    :goto_68
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    const/4 v1, 0x0

    invoke-static {v0, v1}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1400(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;Z)V

    .line 2214
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "mDefaultRunnable run, and ripple()"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2215
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v1, v1, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v2, v2, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v3, v3, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->intensityForRipple:F

    const/high16 v4, 0x40800000    # 4.0f

    mul-float/2addr v3, v4

    const/4 v4, 0x1

    invoke-static {v0, v1, v2, v3, v4}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$000(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;FFFZ)V

    .line 2216
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    const/4 v1, 0x0

    invoke-static {v0, v1}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1502(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;Ljava/lang/Runnable;)Ljava/lang/Runnable;

    .line 2217
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1600(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)V

    .line 2218
    return-void

    .line 2207
    :cond_96
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v1}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1000(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)F

    move-result v1

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1100(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I

    move-result v2

    int-to-float v2, v2

    div-float/2addr v2, v4

    sub-float/2addr v1, v2

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v2, v2, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioAdjustPortrait:F

    sub-float/2addr v1, v2

    iput v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    .line 2208
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v1, v1, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v2, v2, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->XRatioForPortrait:F

    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1100(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I

    move-result v3

    int-to-float v3, v3

    div-float/2addr v2, v3

    mul-float/2addr v1, v2

    iput v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glX:F

    .line 2209
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v1}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1200(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I

    move-result v1

    int-to-float v1, v1

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1300(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)F

    move-result v2

    sub-float/2addr v1, v2

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1200(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I

    move-result v2

    int-to-float v2, v2

    div-float/2addr v2, v4

    sub-float/2addr v1, v2

    iput v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    .line 2210
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v1, v1, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    neg-float v1, v1

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget v2, v2, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->YRatioForPortrait:F

    iget-object v3, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$4;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1200(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)I

    move-result v3

    int-to-float v3, v3

    div-float/2addr v2, v3

    mul-float/2addr v1, v2

    iput v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->glY:F

    goto/16 :goto_68
.end method
