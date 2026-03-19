.class public LGetterSetterSample;
.super Ljava/lang/Object;

.method public constructor <init>()V
    .registers 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

.method public getTitle()Ljava/lang/Object;
    .registers 2
    const/4 v0, 0x0
    return-object v0
.end method

.method public setTitle(Ljava/lang/Object;)V
    .registers 2
    return-void
.end method

.method public static getName(Ljava/lang/Object;)Ljava/lang/Object;
    .registers 2
    const/4 v0, 0x0
    return-object v0
.end method

.method public static setName(Ljava/lang/Object;Ljava/lang/Object;)V
    .registers 2
    return-void
.end method

.method public useGet()V
    .registers 2
    invoke-virtual {p0}, LGetterSetterSample;->getTitle()Ljava/lang/Object;
    move-result-object v0
    return-void
.end method

.method public useSet(Ljava/lang/Object;)V
    .registers 2
    invoke-virtual {p0, p1}, LGetterSetterSample;->setTitle(Ljava/lang/Object;)V
    return-void
.end method

.method public static useGetStatic(Ljava/lang/Object;)V
    .registers 2
    invoke-static {p0}, LGetterSetterSample;->getName(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    return-void
.end method

.method public static useSetStatic(Ljava/lang/Object;Ljava/lang/Object;)V
    .registers 2
    invoke-static {p0, p1}, LGetterSetterSample;->setName(Ljava/lang/Object;Ljava/lang/Object;)V
    return-void
.end method

