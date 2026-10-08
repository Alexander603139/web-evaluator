package com.webevaluator.uiactions;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlaywrightActionsTest {

    private static HttpServer server;
    private static String baseUrl;
    private PlaywrightActions actions;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            try (InputStream in = PlaywrightActionsTest.class.getResourceAsStream("/test-page.html")) {
                byte[] html = in.readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, html.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(html);
                }
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort() + "/";
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop(0);
    }

    @BeforeEach
    void setUp() {
        actions = new PlaywrightActions();
        actions.navigate(baseUrl);
    }

    @AfterEach
    void tearDown() {
        if (actions != null) actions.close();
    }

    @Test
    @DisplayName("кликает 3 раза по кнопке на HTML-фикстуре, проверяет, что JavaScript-счётчик стал равен \"3\"")
    void click_shouldIncrementCounter() {
        actions.click("#click-btn");
        actions.click("#click-btn");
        actions.click("#click-btn");

        String counter = actions.textContent("#click-counter");
        assertThat(counter).isEqualTo("3");
    }

    @Test
    @DisplayName("вводит текст \"hello world\" в поле <input>, проверяет через inputValue(), что значение сохранилось")
    void fill_shouldInputText() {
        actions.fill("#text-input", "hello world");

        String value = actions.inputValue("#text-input");
        assertThat(value).isEqualTo("hello world");
    }

    @Test
    @DisplayName("кликает по кнопке, которая выбрасывает throw new Error('Manual error'), и проверяет, что слушатель onPageError сохранил ошибку в pageErrors")
    void pageError_shouldBeCaught() {
        actions.click("#error-btn");

        assertThat(actions.getPageErrors())
                .hasSizeGreaterThan(0)
                .anyMatch(e -> e.getMessage().contains("Manual error"));
    }

    @Test
    @DisplayName("кликает по ссылке http://localhost:1/nonexistent (заведомо недоступный адрес), проверяет, что onRequestFailed зафиксировал сетевую ошибку")
    void requestFailure_shouldBeCaught() {
        actions.click("#fail-link");
        // Ждём пока браузер попытается загрузить страницу и упадёт
        actions.getPage().waitForTimeout(500);

        assertThat(actions.getNetworkErrors()).hasSizeGreaterThan(0);
    }

    @Test
    @DisplayName("делает скриншот в память, проверяет что вернулся массив байтов >1000 и начинается с PNG-сигнатуры (0x89 0x50)")
    void screenshot_shouldReturnNonEmptyBytes() throws IOException {
        byte[] screenshot = actions.takeScreenshot();

        assertThat(screenshot).isNotNull();
        assertThat(screenshot.length).isGreaterThan(1000);
        // PNG signature: 0x89 0x50 0x4E 0x47
        assertThat(screenshot[0]).isEqualTo((byte) 0x89);
        assertThat(screenshot[1]).isEqualTo((byte) 0x50);
    }

    @Test
    @DisplayName("делает скриншот в память, проверяет что вернулся массив байтов >1000 и начинается с PNG-сигнатуры (0x89 0x50)")
    void screenshot_shouldSaveToFile() throws IOException {
        Path tmpFile = Files.createTempFile("screenshot-", ".png");
        try {
            actions.takeScreenshot(tmpFile);

            assertThat(Files.exists(tmpFile)).isTrue();
            assertThat(Files.size(tmpFile)).isGreaterThan(1000);
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    @Test
    @DisplayName("выбирает value=\"opt2\" в <select>, проверяет что значение сменилось")
    void selectOption_shouldChangeValue() {
        actions.selectOption("#dropdown", "opt2");

        String value = actions.inputValue("#dropdown");
        assertThat(value).isEqualTo("opt2");
    }

    @Test
    @DisplayName("ставит галочку на <input type=\"checkbox\">, проверяет isChecked()=true, затем снимает и проверяет false")
    void check_shouldToggleCheckbox() {
        actions.check("#checkbox");
        assertThat(actions.getPage().isChecked("#checkbox")).isTrue();

        actions.uncheck("#checkbox");
        assertThat(actions.getPage().isChecked("#checkbox")).isFalse();
    }
}