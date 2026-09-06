package com.undertow.users.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.undertow.attention.service.PersonalizationService;
import com.undertow.config.CurrentUser;
import com.undertow.users.dto.UserPreferencesResponse;
import com.undertow.users.service.UserService;

@RestController
@RequestMapping("/api/v1/preferences")
public class PreferencesController {

    private final PersonalizationService personalizationService;
    private final UserService userService;

    public PreferencesController(PersonalizationService personalizationService, UserService userService) {
        this.personalizationService = personalizationService;
        this.userService = userService;
    }

    @GetMapping
    public UserPreferencesResponse get(@CurrentUser String userId) {
        var internalId = userService.getOrCreate(userId).getId();
        return UserPreferencesResponse.from(personalizationService.getOrCreate(internalId));
    }
}
