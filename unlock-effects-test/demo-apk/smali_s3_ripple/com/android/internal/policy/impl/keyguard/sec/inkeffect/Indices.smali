.class public Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Indices;
.super Ljava/lang/Object;
.source "Indices.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Indices$Prog;
    }
.end annotation


# static fields
.field public static final MAX_HANDLER:I = 0xf

.field public static final TEXTURE_HANDLE:I = 0x8

.field public static final g_progHandlers:[I


# direct methods
.method static constructor <clinit>()V
    .registers 1

    .prologue
    .line 7
    const/16 v0, 0xf

    new-array v0, v0, [I

    sput-object v0, Lcom/android/internal/policy/impl/keyguard/sec/inkeffect/Indices;->g_progHandlers:[I

    .line 8
    return-void
.end method

.method public constructor <init>()V
    .registers 1

    .prologue
    .line 3
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 10
    return-void
.end method
