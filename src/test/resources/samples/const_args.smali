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

.method public static k(Ljava/lang/String;)V
    .registers 3
    const-string v0, "Please update the Kotlin runtime to the latest version"
    const-string v1, "this code requires the Kotlin runtime of version at least "
    return-void
.end method

.method public static l(Ljava/lang/String;Ljava/lang/String;)V
    .registers 3
    const-string v0, "Class "
    const-string v1, " is not found."
    return-void
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

.method public static g()V
    .registers 2
    const-string v0, "TAG"
    const-string v1, "refreshData: started"
    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    return-void
.end method

.method public static h()V
    .registers 2
    const-string v0, "TAG"
    const-string v1, "called syncState"
    invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    return-void
.end method

.method public static i(Ljava/lang/String;)V
    .registers 3
    const-string v0, "test: hello "
    invoke-virtual {v0, p0}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;
    move-result-object p0
    const-string v1, "TAG"
    invoke-static {v1, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
    return-void
.end method


