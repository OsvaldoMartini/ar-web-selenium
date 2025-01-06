package com.allinweb.ch.tests;

import java.util.List;

public record ApiResponse<T>(List<T> data, String message, int status) {}
