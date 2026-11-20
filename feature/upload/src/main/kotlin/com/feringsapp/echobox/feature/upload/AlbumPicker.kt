package com.feringsapp.echobox.feature.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feringsapp.echobox.core.repo.AlbumIntent
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 专辑三级选择：不加入 / 新建 / 加入已有。
 * 只产出意图，真正建分支由 Admin 在服务端做。
 */
@Composable
fun AlbumPickerDialog(
    visible: Boolean,
    albums: List<String>,
    initialIntent: AlbumIntent,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (AlbumIntent, String) -> Unit,
) {
    if (!visible) return
    var mode by remember { mutableStateOf(initialIntent) }
    var name by remember { mutableStateOf(initialName) }

    WindowDialog(
        show = true,
        title = "专辑",
        summary = "决定这首曲子放在哪里",
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Card(Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "不加入专辑",
                    summary = if (mode == AlbumIntent.NONE) "已选择" else "作为单曲投稿",
                    onClick = { mode = AlbumIntent.NONE; name = "" },
                )
                BasicComponent(
                    title = "新建专辑",
                    summary = if (mode == AlbumIntent.NEW) "已选择" else "由管理员建新系列",
                    onClick = { mode = AlbumIntent.NEW },
                )
                BasicComponent(
                    title = "加入已有专辑",
                    summary = if (mode == AlbumIntent.EXISTING) "已选择" else "选择现有系列",
                    onClick = { mode = AlbumIntent.EXISTING },
                )
            }

            if (mode == AlbumIntent.NEW) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "新专辑名称",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
            }

            if (mode == AlbumIntent.EXISTING) {
                Text(
                    text = "选择专辑",
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(albums.distinct().take(50)) { album ->
                        BasicComponent(
                            title = album,
                            summary = if (name == album) "已选择" else "",
                            onClick = { name = album },
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "确定",
                    onClick = { onConfirm(mode, name.trim()) },
                    enabled = when (mode) {
                        AlbumIntent.NONE -> true
                        else -> name.isNotBlank()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
