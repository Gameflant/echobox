package com.feringsapp.echobox.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/** 本地投稿记录。进页面时拉一次服务端 pending 目录，用来校正「已处理」状态。 */
@Composable
fun MySubmissionsScreen(onBack: () -> Unit = {}, modifier: Modifier = Modifier) {
    val graph = LocalCoreGraph.current
    val repo = graph.submissionRepository
    val items by repo.observeAll().collectAsState(initial = emptyList())
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        syncing = true
        graph.githubApi.listPendingIds()
            .onSuccess {
                repo.syncStates(it)
                syncMessage = "已同步审核状态"
            }
            .onFailure { syncMessage = "状态同步失败：${it.message}" }
        syncing = false
    }

    EchoScreen(
        title = "我的投稿",
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
            }
        },
    ) {
        if (items.isEmpty()) {
            item {
                EchoGroup {
                    BasicComponent(title = "还没有投稿", summary = "在「上传作品」里提交第一首")
                }
            }
        } else {
            item {
                EchoGroup(title = "共 ${items.size} 条") {
                    items.forEach { entity ->
                        BasicComponent(
                            title = entity.audioName ?: entity.recordId.take(8),
                            summary = when (entity.state) {
                                "DRAFT" -> "草稿 · 未提交"
                                "PENDING" -> "待审核"
                                "PROCESSED" -> "已处理 · 不在待审队列"
                                "APPROVED" -> "已通过"
                                "REJECTED" -> "已退回" + (entity.reason?.let { "：$it" } ?: "")
                                else -> entity.state
                            },
                        )
                    }
                }
            }
        }
        if (syncing || syncMessage.isNotBlank()) {
            item {
                EchoGroup(title = "状态") {
                    BasicComponent(
                        title = if (syncing) "正在同步…" else syncMessage,
                        summary = if (syncing) "" else "以服务端 pending 目录为准",
                    )
                }
            }
        }
    }
}
