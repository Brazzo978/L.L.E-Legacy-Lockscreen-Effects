.class Lcom/android/keyguard/sec/MassRippleUnlockTwin$1;
.super Landroid/os/Handler;
.source "MassRippleUnlockTwin.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/android/keyguard/sec/MassRippleUnlockTwin;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic this$0:Lcom/android/keyguard/sec/MassRippleUnlockTwin;


# direct methods
.method constructor <init>(Lcom/android/keyguard/sec/MassRippleUnlockTwin;)V
    .registers 2

    .prologue
    .line 159
    iput-object p1, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin$1;->this$0:Lcom/android/keyguard/sec/MassRippleUnlockTwin;

    invoke-direct {p0}, Landroid/os/Handler;-><init>()V

    return-void
.end method


# virtual methods
.method public handleMessage(Landroid/os/Message;)V
    .registers 7
    .param p1, "msg"    # Landroid/os/Message;

    .prologue
    const/4 v4, 0x1

    .line 162
    iget v0, p1, Landroid/os/Message;->what:I

    if-nez v0, :cond_12

    .line 163
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin$1;->this$0:Lcom/android/keyguard/sec/MassRippleUnlockTwin;

    iget v1, p1, Landroid/os/Message;->arg1:I

    int-to-float v1, v1

    iget v2, p1, Landroid/os/Message;->arg2:I

    int-to-float v2, v2

    const/4 v3, 0x0

    invoke-virtual {v0, v1, v2, v4, v3}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->rippeDown(FFIZ)V

    .line 168
    :cond_11
    :goto_11
    return-void

    .line 164
    :cond_12
    iget v0, p1, Landroid/os/Message;->what:I

    if-ne v0, v4, :cond_11

    .line 165
    iget-object v0, p0, Lcom/android/keyguard/sec/MassRippleUnlockTwin$1;->this$0:Lcom/android/keyguard/sec/MassRippleUnlockTwin;

    iget v1, p1, Landroid/os/Message;->arg1:I

    int-to-float v1, v1

    iget v2, p1, Landroid/os/Message;->arg2:I

    int-to-float v2, v2

    invoke-virtual {v0, v1, v2, v4, v4}, Lcom/android/keyguard/sec/MassRippleUnlockTwin;->rippeDown(FFIZ)V

    goto :goto_11
.end method
