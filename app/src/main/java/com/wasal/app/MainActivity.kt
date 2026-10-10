
package com.wasal.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.model.Conversation
import com.wasal.app.data.repository.AuthRepository
import com.wasal.app.data.repository.ChatRepository
import com.wasal.app.ui.screens.ChatsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val WasalNavy = Color(0xFF101B3A)
private val WasalBackground = Color(0xFF0B1020)
private val WasalCard = Color(0xFF172342)
private val WasalAccent = Color(0xFF6C8CFF)
private val WasalText = Color(0xFFF4F6FF)
private val WasalMuted = Color(0xFF9AA7C7)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                MaterialTheme {
                    WasalHomeScreen(applicationContext)
                }
            }
        }
    }
}

@Composable
private fun WasalHomeScreen(context: Context) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var conversations by remember {
        mutableStateOf<List<Conversation>>(emptyList())
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val authDataStore = remember(context) {
        AuthDataStore(context)
    }

    val authRepository = remember(authDataStore) {
        AuthRepository(authDataStore)
    }

    // تمرير AuthDataStore حتى يستطيع المستودع تجديد رمز الدخول.
    val chatRepository = remember(authDataStore) {
        ChatRepository(authDataStore)
    }

    LaunchedEffect(authDataStore, authRepository, chatRepository) {
        isLoading = true
        errorMessage = null

        try {
            var token = authDataStore.accessToken.first()

            if (token.isNullOrBlank()) {
                val accountResult = withContext(Dispatchers.IO) {
                    authRepository.createAccount()
                }

                if (accountResult.isFailure) {
                    throw accountResult.exceptionOrNull()
                        ?: IllegalStateException(
                            "تعذر إنشاء حساب وصال."
                        )
                }

                token = authDataStore.accessToken.first()
            }

            if (token.isNullOrBlank()) {
                throw IllegalStateException(
                    "لم يتم حفظ جلسة الدخول."
                )
            }

            val result = withContext(Dispatchers.IO) {
                chatRepository.getConversations(token)
            }

            if (result.isFailure) {
                throw result.exceptionOrNull()
                    ?: IllegalStateException(
                        "تعذر تحميل المحادثات."
                    )
            }

            conversations = result.getOrThrow()

        } catch (exception: Exception) {
            errorMessage = exception.message
                ?: "حدث خطأ أثناء الاتصال بخادم وصال."
        } finally {
            isLoading = false
        }
    }

    val tabs = listOf(
        "المحادثات",
        "جهات الاتصال",
        "المكالمات",
        "الإعدادات"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = WasalBackground,
        contentColor = WasalText,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WasalNavy)
                    .padding(
                        horizontal = 18.dp,
                        vertical = 16.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "وصال",
                        color = WasalText,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = tabs[selectedTab],
                        color = WasalMuted,
                        fontSize = 13.sp
                    )
                }

                IconButton(
                    onClick = { selectedTab = 0 }
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = WasalText,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = WasalNavy,
                contentColor = WasalText
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = "المحادثات"
                        )
                    },
                    label = { Text("المحادثات") }
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            Icons.Default.Contacts,
                            contentDescription = "جهات الاتصال"
                        )
                    },
                    label = { Text("جهات الاتصال") }
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "المكالمات"
                        )
                    },
                    label = { Text("المكالمات") }
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "الإعدادات"
                        )
                    },
                    label = { Text("الإعدادات") }
                )
            }
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    when {
                        isLoading -> StatusSection(
                            title = "جارٍ الاتصال بوصال",
                            message = "يتم تجهيز الحساب وتحميل محادثاتك."
                        )

                        errorMessage != null -> StatusSection(
                            title = "تعذر تحميل المحادثات",
                            message = errorMessage!!
                        )

                        else -> ChatsScreen(
                            conversations = conversations,
                            onConversationClick = {
                                // سيتم ربط فتح المحادثة في خطوة لاحقة.
                            },
                            onSearchClick = {
                                // البحث داخل قائمة المحادثات متاح في الشاشة.
                            }
                        )
                    }
                }

                1 -> StatusSection(
                    title = "جهات الاتصال",
                    message = "سيتم ربط جهات الاتصال برمز وصال المكوّن من 15 حرفًا ورقمًا."
                )

                2 -> StatusSection(
                    title = "المكالمات",
                    message = "ستظهر هنا واجهة المكالمات بعد ربط خادم الوسائط والمكالمات."
                )

                3 -> StatusSection(
                    title = "الإعدادات",
                    message = "إعدادات حساب وصال والخصوصية."
                )
            }
        }
    }
}

@Composable
private fun StatusSection(
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(92.dp),
            shape = CircleShape,
            color = WasalCard
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    tint = WasalAccent,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = title,
            color = WasalText,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = message,
            color = WasalMuted,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = WasalCard,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "وصال — تواصل أقرب",
                color = WasalAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
