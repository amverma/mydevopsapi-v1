package com.cloudcompilerr.devops.controller;

import java.util.Collections;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MySampleController {

	@GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
	public List<String> getUserNames() {
		return Collections.singletonList("Manish");
	}

}
