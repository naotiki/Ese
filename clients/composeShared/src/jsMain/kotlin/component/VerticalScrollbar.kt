package component

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun VerticalScrollbar(
    modifier: Modifier,
    adapter: LazyListState,
    reverseLayout: Boolean
) {
    androidx.compose.foundation.VerticalScrollbar(
        modifier = modifier,
        adapter = rememberScrollbarAdapter(
            scrollState = adapter
        ),
        reverseLayout = reverseLayout

    )
}

@Composable
actual fun SelectionContainer(content: @Composable () -> Unit) {
    content()
}

actual fun getSystemLineSeparator(): String ="\n"
