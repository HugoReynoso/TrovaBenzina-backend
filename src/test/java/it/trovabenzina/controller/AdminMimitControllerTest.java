package it.trovabenzina.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import it.trovabenzina.exception.GlobalExceptionHandler;
import it.trovabenzina.exception.MimitDownloadException;
import it.trovabenzina.integration.mimit.MimitImportService;
import it.trovabenzina.service.FrontendRebuildService;

class AdminMimitControllerTest {
	@Test
	void downloadFailureReturns502AndDoesNotTriggerFrontendBuild() throws Exception {
		MimitImportService imports = mock(MimitImportService.class);
		FrontendRebuildService rebuild = mock(FrontendRebuildService.class);
		when(imports.importData()).thenThrow(new MimitDownloadException("MIMIT download timed out",
				new HttpTimeoutException("request timed out")));
		var mvc = MockMvcBuilders.standaloneSetup(new AdminMimitController(imports, rebuild))
				.setControllerAdvice(new GlobalExceptionHandler()).build();
		mvc.perform(post("/api/admin/mimit/import"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value("MIMIT download timed out"));
		verifyNoInteractions(rebuild);
	}
}
