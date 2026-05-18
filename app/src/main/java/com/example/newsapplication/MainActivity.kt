package com.example.newsapplication

import android.content.Intent
import android.net.Uri
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

// Корневой объект ответа
@Serializable
data class RawResponse(
    val status: String,
    val copyright: String,
    val num_results: Int,
    val results: List<RawArticle>
)
// Входные необработанные данные
@Serializable
data class RawArticle(
    val title: String,
    val url: String,
    @SerialName("adx_keywords") val adxKeywords: String?,
    val abstract:  String?,
    val media: List<Media>?,
    @SerialName("published_date") val publishedDate: String, // Имя в JSON отличается от имени в классе
    val source: String
)

// Данные, обработавшие раздел с изображениями
data class Article(
    val title: String,
    val url: String,
    val description: String?,
    val adxKeywords: String?,
    val image: String?,
    val publishedAt: String,
    val source: String
)

// Медиа-объект
@Serializable
data class Media(
    val type: String,
    @SerialName("media-metadata") val mediaMetadata: List<ImageMeta>
)

// Метаданные изображения
@Serializable
data class ImageMeta(
    val url: String,
    val format: String,
    val width: Int,
    val height: Int
)


// Сетевой клиент
val client = HttpClient(Android) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            allowSpecialFloatingPointValues = true
        })
    }
}

const val API_KEY = "P7AfnhgqGaseQv7IJqm6o3LlTcSPB4aMrC8YhClGN7Al006v"
const val URL = "https://api.nytimes.com/svc/mostpopular/v2/emailed/30.json?api-key=$API_KEY"

// Основной экран
class MainActivity :
    ComponentActivity() {
    private fun openUrlInBrowser(url: String) {
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url)
            )
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(
                this,
                "Не удалось открыть ссылку",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NewsMainScreen()
            }
        }
    }
}

// Функция конвертации объекта из сырых входныых данных в объект Article
fun RawArticle.toAppArticle(): Article {
    val imageUrl = this.media
        ?.firstOrNull { it.type == "image" }
        ?.mediaMetadata
        ?.maxByOrNull { it.width }
        ?.url

    return Article(
        title = this.title,
        url = this.url,
        description = this.abstract,
        adxKeywords = this.adxKeywords,
        image = imageUrl,
        publishedAt = this.publishedDate,
        source = this.source
    )
}
// Экран с новостями
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsMainScreen() {
    var newsList by remember { mutableStateOf<List<Article>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                isLoading = true
                errorMessage = null

                val response: RawResponse = client.get(URL).body()
                val adaptedList = response.results.map {rawArticle ->
                    rawArticle.toAppArticle()
                }
                newsList = adaptedList

            } catch (e: Exception) {
                errorMessage = "Ошибка загрузки: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Популярные новости за 30 дней",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    ) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF26B8A),
                    titleContentColor = Color(0xFF474A50)
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(Color(0xFFFFC0CB))) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                errorMessage != null -> {
                    Text(text = errorMessage!!, modifier = Modifier.align(Alignment.Center), color = Color.Red)
                }
                newsList.isEmpty() -> {
                    Text(text = "Нет новостей", modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(newsList) { article ->
                            NewsCard (article = article)
                        }
                    }
                }
            }
        }
    }
}

// Карточка новости
@Composable
fun NewsCard(article: Article) {
    val context = LocalContext.current
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                }
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Заголовок
            Text(
                text = article.title,
                fontSize = 18.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Источник и Дата
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge {
                    Text(text = article.source, fontFamily = FontFamily.Serif, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDate(article.publishedAt),
                    fontFamily = FontFamily.Serif,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Описание
            if (!article.description.isNullOrBlank()) {
                Text(
                    text = article.description,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4
                )
            } else {
                Text("Описание недоступно", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color.Gray)
            }

            // Ключевые слова
            if (!article.adxKeywords.isNullOrBlank()) {
                Text("Keywords: ", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = article.adxKeywords,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4
                )
            } else {
                Text("Нет ключевых слов", fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color.Gray)
            }

            if (!article.image.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))

                AsyncImage(
                    model = article.image,
                    contentDescription = "News photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

// Преобразование даты в удобочитаемый формат
fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString)

        val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale("ru"))
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        dateString
    }
}

