package com.example.seleniumdemo.controller;

import com.example.seleniumdemo.model.FlowSession;
import com.example.seleniumdemo.model.StartFlowRequest;
import com.example.seleniumdemo.service.SeleniumFlowService;
import com.example.seleniumdemo.service.SessionRegistry;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
