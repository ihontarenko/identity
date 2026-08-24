package net.innoventa.identity.web.rest.dto;

public record ApplicationLinksResponse(
    String centralUrl, String innoventaUrl, String kiwiUrl, String tesseraUrl) {
}
