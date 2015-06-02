/**
 * Copyright (C) 2015 Born Informatik AG (www.born.ch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wte4j.ui.server.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.wte4j.ExpressionError;
import org.wte4j.InvalidTemplateException;
import org.wte4j.LockingException;
import org.wte4j.MappingDetail;
import org.wte4j.Template;
import org.wte4j.TemplateBuilder;
import org.wte4j.TemplateEngine;
import org.wte4j.TemplateExistException;
import org.wte4j.TemplateFile;
import org.wte4j.TemplateRepository;
import org.wte4j.WteException;
import org.wte4j.WteModelService;
import org.wte4j.ui.shared.InvalidTemplateServiceException;
import org.wte4j.ui.shared.MappingDto;
import org.wte4j.ui.shared.ModelElementDto;
import org.wte4j.ui.shared.TemplateDto;
import org.wte4j.ui.shared.TemplateService;
import org.wte4j.ui.shared.TemplateServiceException;

@Service
public class TemplateServiceImpl implements TemplateService {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private ServiceContext serviceContext;

	@Autowired
	@Qualifier("wte4j-admin")
	private MessageFactory messageFactory;

	@Autowired
	private TemplateEngine templateEngine;

	@Autowired
	private TemplateRepository templateRepository;

	@Autowired
	private WteModelService modelService;

	@Override
	public List<TemplateDto> getTemplates() {
		List<Template<Object>> templates = templateRepository.queryTemplates()
				.list();
		List<TemplateDto> templateDtos = new ArrayList<TemplateDto>();
		for (Template<Object> template : templates) {
			TemplateDto templateDto = DtoFactory.createTemplateDto(template);
			templateDtos.add(templateDto);
		}
		return templateDtos;
	}

	@Override
	public TemplateDto lockTemplate(TemplateDto templateDto) {
		Template<?> template = lookup(templateDto);
		try {
			template = templateRepository.lockForEdit(template, serviceContext.getUser());
			return DtoFactory.createTemplateDto(template);
		} catch (LockingException e) {
			logger.debug("template {}_{} is locked by {}",
					template.getDocumentName(),
					template.getLanguage(),
					template.getLockingUser().getDisplayName(),
					e);
			throw createServiceException(MessageKey.LOCKED_TEMPLATE);
		}
	}

	@Override
	public TemplateDto unlockTemplate(TemplateDto templateDto) {
		Template<?> template = lookup(templateDto);
		template = templateRepository.unlock(template);
		return DtoFactory.createTemplateDto(template);
	}

	@Override
	public void deleteTemplate(TemplateDto templateDto) {
		Template<?> template = lookup(templateDto);
		try {
			templateRepository.delete(template);

		} catch (LockingException e) {
			logger.debug("template {}_{} is locked by {}",
					template.getDocumentName(),
					template.getLanguage(),
					template.getLockingUser().getDisplayName(), e);
			throw createServiceException(MessageKey.LOCKED_TEMPLATE);
		}

	}

	@Override
	public List<ModelElementDto> listModelElements(String inputType, Map<String, String> properties) throws TemplateServiceException {
		try {
			Class<?> inputClass = Class.forName(inputType);
			Map<String, Class<?>> modelElements = modelService.listModelElements(inputClass, properties);

			List<ModelElementDto> dtos = new ArrayList<>(modelElements.size());
			for (Map.Entry<String, Class<?>> entry : modelElements.entrySet()) {
				ModelElementDto dto = new ModelElementDto();
				dto.setName(entry.getKey());
				dto.setType(entry.getValue().getName());
				dtos.add(dto);
			}
			return dtos;

		} catch (ClassNotFoundException e) {
			throw createServiceException(MessageKey.TEMPLATE_CLASS_NOT_FOUND, e);
		} catch (RuntimeException e) {
			throw createServiceException(MessageKey.INTERNAL_SERVER_ERROR, e);
		}

	}

	@Override
	public TemplateDto saveTemplateData(TemplateDto templateDto, String uploadedTemplate) throws TemplateServiceException {
		Template<?> template = lookup(templateDto);
		Path path = Paths.get(uploadedTemplate);
		try (InputStream in = Files.newInputStream(path)) {
			template.update(in, serviceContext.getUser());
			copyMappingData(template.getContentMapping(), templateDto.getMapping());
			template.validate();
			template = templateRepository.persist(template);
			return DtoFactory.createTemplateDto(template);
		} catch (IOException e) {
			throw createServiceException(MessageKey.INTERNAL_SERVER_ERROR);
		} catch (LockingException e) {
			throw createServiceException(MessageKey.LOCKED_TEMPLATE);
		} catch (InvalidTemplateException e) {
			throw createInvalidTemplateServiceException(e.getErrors());
		}
	}

	@Override
	public TemplateDto createTemplate(TemplateDto newTemplate, String templateFile) {
		try {
			Class<?> clazz = Class.forName(newTemplate.getInputType());
			Map<String, MappingDetail> mappingData = new HashMap<String, MappingDetail>();
			copyMappingData(mappingData, newTemplate.getMapping());

			TemplateBuilder<?> templateBuilder = templateEngine.getTemplateBuilder(clazz);

			templateBuilder
					.setAuthor(serviceContext.getUser())
					.setDocumentName(newTemplate.getDocumentName())
					.setLanguage(newTemplate.getLanguage())
					.setMappingData(mappingData);

			if (StringUtils.isNotEmpty(templateFile)) {
				templateBuilder.setTemplateFile(Paths.get(templateFile));
			}

			Template<?> template = templateBuilder.build();
			template.validate();
			template = templateRepository.persist(template);
			return DtoFactory.createTemplateDto(template);
		} catch (ClassNotFoundException e) {
			throw createServiceException(MessageKey.TEMPLATE_CLASS_NOT_FOUND, e);
		} catch (TemplateExistException e) {
			throw createServiceException(MessageKey.TEMPLATE_EXISTS);
		} catch (InvalidTemplateException e) {
			throw createInvalidTemplateServiceException(e.getErrors());
		} catch (RuntimeException e) {
			logger.error("error on creating template", e);
			throw createServiceException(MessageKey.INTERNAL_SERVER_ERROR, e);
		}
	}

	private static void copyMappingData(Map<String, MappingDetail> target, List<MappingDto> source) {
		target.clear();
		for (MappingDto dto : source) {
			if (StringUtils.isNotBlank(dto.getModelKey())
					|| StringUtils.isNotBlank(dto.getFormatterDefinition())) {
				MappingDetail mappingDetail = new MappingDetail();
				mappingDetail.setModelKey(dto.getModelKey());
				mappingDetail.setFormatterDefinition(dto.getFormatterDefinition());
				target.put(dto.getConentControlKey(), mappingDetail);
			}
		}

	}

	Template<?> lookup(TemplateDto dto) {
		Template<?> template = templateRepository.getTemplate(dto.getDocumentName(), dto.getLanguage());
		if (template == null) {
			throw createServiceException(MessageKey.TEMPLATE_NOT_FOUND);
		}
		return template;
	}

	TemplateServiceException createServiceException(MessageKey key) {
		String message = messageFactory.createMessage(key.getValue());
		return new TemplateServiceException(message);
	}

	TemplateServiceException createServiceException(MessageKey key, Throwable cause) {
		String message = messageFactory.createMessage(key.getValue());
		return new TemplateServiceException(message, cause);
	}

	InvalidTemplateServiceException createInvalidTemplateServiceException(Map<String, ExpressionError> errors) {
		String message = messageFactory.createMessage(MessageKey.UPLOADED_FILE_NOT_VALID.getValue());
		List<String> details = new ArrayList<String>();
		for (Map.Entry<String, ExpressionError> entry : errors.entrySet()) {
			String detailMessage = messageFactory.createMessage("wte4j.message." + entry.getValue().name(), entry.getKey());
			details.add(detailMessage);
		}
		return new InvalidTemplateServiceException(message, details);
	}

	@Override
	public List<String> listContendIds(String pathToFile) throws TemplateServiceException {
		Path filePath = Paths.get(pathToFile);
		if (!Files.exists(filePath)) {
			throw createServiceException(MessageKey.TEMPLATE_NOT_FOUND);
		}
		try {
			TemplateFile templateFile = templateEngine.asTemplateFile(filePath);
			return templateFile.listContentIds();
		} catch (IOException e) {
			throw createServiceException(MessageKey.INTERNAL_SERVER_ERROR, e);
		} catch (WteException e) {
			logger.debug("error on parsing file {}", pathToFile, e);
			throw createServiceException(MessageKey.UPLOADED_FILE_NOT_VALID, e);
		}
	}
}