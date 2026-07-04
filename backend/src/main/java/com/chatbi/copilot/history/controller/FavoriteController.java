package com.chatbi.copilot.history.controller;

import com.chatbi.copilot.common.ApiResponse;
import com.chatbi.copilot.history.dto.FavoriteReq;
import com.chatbi.copilot.history.entity.Favorite;
import com.chatbi.copilot.history.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Favorites", description = "Saved questions")
@RestController
@RequestMapping("/favorites")
public class FavoriteController {

    private final FavoriteService service;

    public FavoriteController(FavoriteService service) {
        this.service = service;
    }

    @Operation(summary = "List favorites")
    @GetMapping
    public ApiResponse<List<Favorite>> list(@RequestParam(required = false) Long datasourceId) {
        return ApiResponse.ok(service.list(datasourceId));
    }

    @Operation(summary = "Save a favorite")
    @PostMapping
    public ApiResponse<Favorite> create(@Valid @RequestBody FavoriteReq req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Delete a favorite")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
