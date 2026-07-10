.class Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;
.super Landroid/os/Handler;
.source "CircleUnlockRippleRenderer.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = "DVFSHandlerForRipple"
.end annotation


# instance fields
.field final synthetic this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;


# direct methods
.method constructor <init>(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)V
    .registers 2

    .prologue
    .line 2933
    iput-object p1, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-direct {p0}, Landroid/os/Handler;-><init>()V

    return-void
.end method


# virtual methods
.method public handleMessage(Landroid/os/Message;)V
    .registers 6
    .param p1, "msg"    # Landroid/os/Message;

    .prologue
    const/4 v3, 0x1

    const/4 v2, 0x0

    const/4 v1, 0x0

    .line 2937
    iget v0, p1, Landroid/os/Message;->what:I

    packed-switch v0, :pswitch_data_98

    .line 2988
    :cond_8
    :goto_8
    invoke-super {p0, p1}, Landroid/os/Handler;->handleMessage(Landroid/os/Message;)V

    .line 2989
    return-void

    .line 2940
    :pswitch_c
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1700(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)V

    goto :goto_8

    .line 2944
    :pswitch_12
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v0}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1800(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)V

    goto :goto_8

    .line 2948
    :pswitch_18
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    if-eqz v0, :cond_27

    .line 2950
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0, v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->releaseBooster(I)V

    .line 2951
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iput-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    .line 2954
    :cond_27
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "== DVFS == new cpuMaxClockBooster"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2955
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    new-instance v1, Landroid/os/DVFSHelper;

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1900(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)Landroid/content/Context;

    move-result-object v2

    const/16 v3, 0xd

    invoke-direct {v1, v2, v3}, Landroid/os/DVFSHelper;-><init>(Landroid/content/Context;I)V

    iput-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    goto :goto_8

    .line 2959
    :pswitch_40
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    if-eqz v0, :cond_4f

    .line 2961
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0, v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->releaseBooster(I)V

    .line 2962
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iput-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    .line 2965
    :cond_4f
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "== DVFS == new gpuMaxFreqBooster"

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    .line 2966
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    new-instance v1, Landroid/os/DVFSHelper;

    iget-object v2, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-static {v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->access$1900(Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;)Landroid/content/Context;

    move-result-object v2

    const/16 v3, 0x11

    invoke-direct {v1, v2, v3}, Landroid/os/DVFSHelper;-><init>(Landroid/content/Context;I)V

    iput-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    goto :goto_8

    .line 2970
    :pswitch_68
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    if-eqz v0, :cond_8

    .line 2972
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0, v2}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->releaseBooster(I)V

    .line 2973
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iput-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->cpuMaxClockBooster:Landroid/os/DVFSHelper;

    .line 2974
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "== DVFS == cpuMaxClockBooster => null "

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto :goto_8

    .line 2979
    :pswitch_7f
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iget-object v0, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    if-eqz v0, :cond_8

    .line 2981
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    invoke-virtual {v0, v3}, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->releaseBooster(I)V

    .line 2982
    iget-object v0, p0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer$DVFSHandlerForRipple;->this$0:Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;

    iput-object v1, v0, Lcom/android/internal/policy/impl/keyguard/sec/CircleUnlockRippleRenderer;->gpuMaxFreqBooster:Landroid/os/DVFSHelper;

    .line 2983
    const-string v0, "CircleUnlockRippleRenderer"

    const-string v1, "== DVFS == gpuMaxFreqBooster => null "

    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I

    goto/16 :goto_8

    .line 2937
    nop

    :pswitch_data_98
    .packed-switch 0x0
        :pswitch_c
        :pswitch_12
        :pswitch_18
        :pswitch_40
        :pswitch_68
        :pswitch_7f
    .end packed-switch
.end method
