package com.webevaluator.uiactions;

import com.microsoft.playwright.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
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
                .setHeadless(true));
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