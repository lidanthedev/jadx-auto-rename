.class LConstArgsSample;
.super Ljava/lang/Object;

.method public static checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V
    .registers 2
    return-void
.end method

.method public static notNull(Ljava/lang/Object;Ljava/lang/String;)V
    .registers 2
    return-void
.end method

.method public static p(Ljava/lang/Object;Ljava/lang/String;)V
    .registers 2
    if-nez p0, :ok
    invoke-static {p1}, LConstArgsSample;->O(Ljava/lang/String;)V
    :ok
    return-void
.end method

.method public static O(Ljava/lang/String;)V
    .registers 2
    new-instance v0, Ljava/lang/NullPointerException;
    invoke-direct {v0, p0}, Ljava/lang/NullPointerException;-><init>(Ljava/lang/String;)V
    throw v0
.end method

.method public static get(Ljava/lang/String;)Ljava/lang/Object;
    .registers 1
    const/4 v0, 0x0
    return-object v0
.end method

.method public static demo(Ljava/lang/Object;Ljava/lang/Object;)V
    .registers 7
    const-string v0, "innerPadding"
    invoke-static {p0, v0}, LConstArgsSample;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V
    const-string v1, "activity"
    invoke-static {p1, v1}, LConstArgsSample;->notNull(Ljava/lang/Object;Ljava/lang/String;)V
    const-string v2, "conditions"
    invoke-static {v2}, LConstArgsSample;->get(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v3
    const-string v4, "conditions"
    invoke-static {v3, v4}, LConstArgsSample;->notNull(Ljava/lang/Object;Ljava/lang/String;)V
    return-void
.end method

.method public static demoObf(Ljava/lang/Object;)V
    .registers 2
    const-string v0, "name"
    invoke-static {p0, v0}, LConstArgsSample;->p(Ljava/lang/Object;Ljava/lang/String;)V
    return-void
.end method


