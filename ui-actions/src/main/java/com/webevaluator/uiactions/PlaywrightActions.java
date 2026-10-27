package com.webevaluator.uiactions;

import com.microsoft.playwright.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.microsoft.playwright.options.LoadState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class PlaywrightActions implements AutoCloseable {

    private final Playwright playwright;
    private final Browser browser;
    @Getter private final BrowserContext context;
    @Getter private final Page page;

    @Getter private final List<PageError> pageErrors = new ArrayList<>();
    @Getter private final List<NetworkError> networkErrors = new ArrayList<>();

    public PlaywrightActions() {
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(Arrays.asList("--no-sandbox", "--disable-setuid-sandbox")));
        this.context = browser.newContext();
        this.page = context.newPage();

        page.onPageError(error -> {
            log.warn("JS error: {}", error);
            pageErrors.add(new PageError(error, null));
        });

        page.onRequestFailed(request ->
                networkErrors.add(new NetworkError(
                        request.url(),
                        request.method(),
                        request.failure() != null ? request.failure() : "unknown"
                ))
        );
    }

    public void navigate(String url) {
        page.navigate(url);
    }

    public void click(String selector) {
        page.click(selector);
    }

    public void doubleClick(String selector) {
        page.dblclick(selector);
    }

    public void hover(String selector) {
        page.hover(selector);
    }

    public void fill(String selector, String text) {
        page.fill(selector, text);
    }

    public void pressKey(String selector, String key) {
        page.press(selector, key);
    }

    public void scroll(String selector, int deltaX, int deltaY) {
        page.mouse().wheel(deltaX, deltaY);
    }

    public void dragAndDrop(String sourceSelector, String targetSelector) {
        page.dragAndDrop(sourceSelector, targetSelector);
    }

    public void selectOption(String selector, String value) {
        page.selectOption(selector, value);
    }

    public void check(String selector) {
        page.check(selector);
    }

    public void uncheck(String selector) {
        page.uncheck(selector);
    }

    public String inputValue(String selector) {
        return page.inputValue(selector);
    }

    public String textContent(String selector) {
        return page.textContent(selector);
    }

    public void triggerJsError() {
        page.evaluate("setTimeout(() => { throw new Error('Test JS error'); }, 0)");
        page.waitForTimeout(100);
    }

    public byte[] takeScreenshot() {
        return page.screenshot();
    }

    public byte[] takeScreenshot(Path path) {
        return page.screenshot(new Page.ScreenshotOptions().setPath(path));
    }

    public String getTitle() {
        return page.title();
    }

    public List<String> findInteractiveElements() {
        List<String> selectors = new ArrayList<>();

        // Кнопки
        List<Locator> buttons = page.locator("button:visible").all();
        for (int i = 0; i < buttons.size(); i++) {
            selectors.add("button >> nth=" + i);
        }

        // Ссылки
        List<Locator> links = page.locator("a[href]:visible").all();
        for (int i = 0; i < links.size(); i++) {
            selectors.add("a[href] >> nth=" + i);
        }

        // Поля ввода
        List<Locator> inputs = page.locator("input:visible, textarea:visible").all();
        for (int i = 0; i < inputs.size(); i++) {
            selectors.add("input, textarea >> nth=" + i);
        }

        // Элементы с role='button'
        List<Locator> roleButtons = page.locator("[role='button']:visible").all();
        for (int i = 0; i < roleButtons.size(); i++) {
            selectors.add("[role='button'] >> nth=" + i);
        }

        return selectors;
    }

    public String getElementType(String selector) {
        Locator loc = parseSelector(selector);
        return loc.evaluate("el => el.tagName.toLowerCase()").toString();
    }

    public void clickWithForce(String selector) {
        parseSelector(selector).click(new Locator.ClickOptions().setForce(true));
        wait(200);
    }

    public void type(String selector, String text) {
        parseSelector(selector).fill(text);
        wait(100);
    }

    public void pressEnter() {
        page.keyboard().press("Enter");
        wait(100);
    }

    public void scroll(int deltaX, int deltaY) {
        page.mouse().wheel(deltaX, deltaY);
        wait(100);
    }

    public void waitForLoadState(LoadState state) {
        page.waitForLoadState(state);
    }

    public void clearErrors() {
        pageErrors.clear();
        networkErrors.clear();
    }

    public String generateRandomText() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt((int)(Math.random() * chars.length())));
        }
        return sb.toString();
    }

    private Locator parseSelector(String selector) {
        if (selector.contains(">> nth=")) {
            String[] parts = selector.split(" >> nth=");
            int index = Integer.parseInt(parts[1]);
            return page.locator(parts[0]).nth(index);
        }
        return page.locator(selector);
    }

    private void wait(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void waitForLoadState(String state) {
        page.waitForLoadState(LoadState.valueOf(state.toUpperCase()));
    }

    public void setExtraHeader(String name, String value) {
        if (context != null) {
            context.setExtraHTTPHeaders(java.util.Map.of(name, value));
        }
    }

    @Override
    public void close() {
        try {
            if (page != null) page.close();
            if (context != null) context.close();
            if (browser != null) browser.close();
            if (playwright != null) playwright.close();
        } catch (Exception e) {
            log.warn("Error during Playwright cleanup", e);
        }
    }
}