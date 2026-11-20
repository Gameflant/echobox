package com.feringsapp.echobox

import android.app.Application
import com.feringsapp.echobox.core.CoreGraph
import com.feringsapp.echobox.core.model.RepoConfig

class EchoBoxApplication : Application() {

    val graph: CoreGraph by lazy {
        CoreGraph(
            context = applicationContext,
            repoConfig = RepoConfig(
                owner = BuildConfig.PROD_OWNER,
                repo = BuildConfig.PROD_REPO,
                branch = BuildConfig.PROD_BRANCH,
            ),
            appVersion = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            updateOwner = BuildConfig.UPDATE_OWNER,
            updateRepo = BuildConfig.UPDATE_REPO,
            githubPat = BuildConfig.GITHUB_PAT,
            stagingOwner = BuildConfig.STAGING_OWNER,
            stagingRepo = BuildConfig.STAGING_REPO,
            stagingTag = BuildConfig.STAGING_TAG,
        )
    }

    override fun onCreate() {
        super.onCreate()
        // 不阻塞首帧：warmUp 内部全部在 Default 调度器上跑。
        graph.warmUp()
    }
}
