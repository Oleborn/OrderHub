package oleborn.reactivenotificationservice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${app.provider.url}")
    private String providerUrl;

    /**
     * Создаёт бин WebClient — реактивный HTTP-клиент для вызова провайдера.
     * Все настройки ориентированы на production: таймауты, пул соединений, обработка ошибок.
     */
    @Bean
    public WebClient providerWebClient(WebClient.Builder builder) {

        // ========================================================================
        // 1. НАСТРОЙКА ПУЛА СОЕДИНЕНИЙ (ConnectionProvider)
        // ========================================================================
        // WebClient (через Reactor Netty) использует пул TCP-соединений для оптимизации.
        // Без явной настройки будет использоваться пул по умолчанию, который может
        // не подходить для высокой нагрузки.
        //
        // ConnectionProvider.builder("provider-pool") — создаём пул с именем,
        // чтобы его было видно в метриках и логах.
        ConnectionProvider connectionProvider = ConnectionProvider.builder("provider-pool")

                // Максимальное количество одновременно открытых соединений в пуле.
                // При превышении лимита новые запросы будут ждать (см. pendingAcquireTimeout).
                // Для внешнего API с лимитом 50 RPS достаточно 50 соединений — больше не понадобится.
                .maxConnections(50)

                // Максимальное время, которое простаивающее соединение может жить в пуле.
                // Если соединение не используется дольше 5 минут — оно закрывается и удаляется.
                // Это предотвращает «залипание» старых соединений, которые могут быть
                // разорваны на стороне провайдера (например, из-за таймаута на его балансировщике).
                .maxIdleTime(Duration.ofMinutes(5))

                // Максимальное общее время жизни соединения с момента его создания.
                // Даже если соединение активно используется, через 10 минут оно будет закрыто
                // и заменено новым. Это защищает от проблем с долгоживущими соединениями
                // (например, утечек памяти на уровне ОС или TLS-сессий).
                .maxLifeTime(Duration.ofMinutes(10))

                // Таймаут ожидания свободного соединения из пула.
                // Если все соединения заняты, а новых создать нельзя (достигнут maxConnections),
                // запрос будет ждать до 5 секунд, после чего получит ошибку.
                // Это позволяет быстро отказывать, а не висеть бесконечно, если провайдер перегружен.
                .pendingAcquireTimeout(Duration.ofSeconds(5))

                // Фоновый поток для проверки и очистки пула от «мёртвых» соединений.
                // Каждые 120 секунд пул проверяет соединения на валидность и удаляет те,
                // которые превысили maxIdleTime или maxLifeTime.
                .evictInBackground(Duration.ofSeconds(120))

                .build();

        // ========================================================================
        // 2. НАСТРОЙКА НИЗКОУРОВНЕВОГО HTTP-КЛИЕНТА (HttpClient от Reactor Netty)
        // ========================================================================
        // HttpClient — это ядро, которое управляет сетевыми операциями.
        // Мы настраиваем его поверх нашего пула соединений.
        HttpClient httpClient = HttpClient.create(connectionProvider)

                // Таймаут установки TCP-соединения (connect timeout).
                // Если за 5 секунд не удалось установить соединение с провайдером,
                // запрос завершится ошибкой. Это защищает от зависания при проблемах с сетью.
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)

                // Общий таймаут ожидания ответа (response timeout).
                // Отсчитывается с момента отправки запроса до получения полного ответа.
                // Если провайдер не отвечает в течение 5 секунд — запрос прерывается.
                // Важно: это общий таймаут, включающий и время на чтение тела ответа.
                .responseTimeout(Duration.ofSeconds(5))

                // Дополнительные таймауты на уровне обработчиков Netty (более точные).
                // doOnConnected — срабатывает после установки соединения, до отправки запроса.
                .doOnConnected(conn -> conn
                        // ReadTimeoutHandler — максимальное время ожидания данных от провайдера
                        // после того, как соединение установлено. Если за 5 секунд не пришло
                        // ни одного байта (например, провайдер завис), соединение прерывается.
                        .addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS))
                        // WriteTimeoutHandler — максимальное время отправки данных провайдеру.
                        // Если за 5 секунд не удалось отправить весь запрос (например, сеть медленная),
                        // соединение прерывается.
                        .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS))
                );

        // ========================================================================
        // 3. ПОСТРОЕНИЕ FINAL BEAN WebClient
        // ========================================================================
        // Используем стандартный Spring Builder, добавляем базовый URL,
        // подключаем наш сконфигурированный HttpClient через ReactorClientHttpConnector.
        // Остальные настройки (сериализация, логирование, фильтры) можно добавить здесь же.
        return builder
                // Базовый URL — все относительные пути будут дополняться этим префиксом.
                // Например, webClient.post().uri("/notify") → POST https://provider.com/notify
                .baseUrl(providerUrl)

                // Подключаем настроенный HttpClient как коннектор.
                // Без этого WebClient будет использовать стандартный HttpClient по умолчанию,
                // и все наши настройки пула и таймаутов не применятся.
                .clientConnector(new ReactorClientHttpConnector(httpClient))

                // Собираем и регистрируем бин.
                .build();
    }
}