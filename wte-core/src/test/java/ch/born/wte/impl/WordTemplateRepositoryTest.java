package ch.born.wte.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;

import ch.born.wte.FileStore;
import ch.born.wte.FormatterFactory;
import ch.born.wte.LockingException;
import ch.born.wte.Template;
import ch.born.wte.TemplateQuery;
import ch.born.wte.TestWithEmbeddedDataSource;
import ch.born.wte.User;
import ch.born.wte.WteModelService;

import static org.junit.Assert.*;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

@TransactionConfiguration
public class WordTemplateRepositoryTest extends TestWithEmbeddedDataSource {
	private static final long LOCKED_TEMPLATE = 2;

	private static final long UNLOCKED_TEMPLATE = 1;

	@PersistenceContext
	EntityManager entityManager;

	FormatterFactory formatterFactory = mock(FormatterFactory.class);

	WteModelService modelService = mock(WteModelService.class);

	WordTemplateRepository repository;

	@Before
	public void initTest() throws Exception {
		repository = new WordTemplateRepository();
		repository.em = entityManager;
		repository.formatterFactory = formatterFactory;
		repository.modelService = modelService;
	}

	@Test
	@Transactional
	public void testQueryTemplates() {
		TemplateQuery query = repository.queryTemplates();
		List<Template<Object>> templates = query.list();
		assertEquals(4, templates.size());
	}

	@Test
	@Transactional
	public void testQueryWithEmptyResults() {
		TemplateQuery query = repository.queryTemplates();
		List<Template<Object>> templates = query.language("xxx").list();
		assertEquals(0, templates.size());
	}

	@Test
	@Transactional
	public void getTemplate() {
		Template<?> template = repository.getTemplate("test1", "en");
		assertNotNull(template);
	}

	@Test
	@Transactional
	public void getNonExistingTemplate() {
		Template<?> template = repository.getTemplate("XXXX", "XX");
		assertNull(template);
	}

	@Test(expected = IllegalArgumentException.class)
	@Transactional
	public void getTemplateWithWrongInputType() {
		repository.getTemplate("test1", "en", Date.class);
		fail("Exception expected");
	}

	@Test
	@Transactional
	public void locking() {
		WordTemplate<?> template = unlockedTemplate();

		User lockingUser = new User("locking", "Locking User");
		Template<?> locked = repository.lockForEdit(template, lockingUser);
		assertEquals(lockingUser, locked.getLockingUser());
		assertTrue(isVersionIncontext(locked));
	}

	@Test
	@Transactional
	public void lockingLockedTemplate() {
		WordTemplate<?> toLock = lockedTemplate();
		User second = new User("second", "Second User");
		try {
			repository.lockForEdit(toLock, second);
			fail(LockingException.class + " expected");
		} catch (LockingException e) {
			assertTrue(isVersionIncontext(toLock));
		}
	}

	@Test(expected = LockingException.class)
	@Transactional
	public void lockingTemplateParallel() {
		Template<?> template1 = unlockedTemplate();
		Template<?> template2 = unlockedTemplate();

		template1 = repository.lockForEdit(template1,
				new User("first", "First"));

		User second = new User("second", "Second User");
		repository.lockForEdit(template2, second);
		fail(LockingException.class + " expected");
	}

	@Test
	@Transactional
	public void lockingTwice() {
		Template<?> template = unlockedTemplate();
		User user = new User("user", "User");
		template = repository.lockForEdit(template, user);
		template = repository.lockForEdit(template, user);
		assertEquals(user, template.getLockingUser());
	}

	@Test
	@Transactional
	public void unlock() {
		WordTemplate<?> template = lockedTemplate();

		Template<?> unlocked = repository.unlock(template);
		assertNull(unlocked.getLockingUser());
		assertTrue(isVersionIncontext(unlocked));
	}

	@Test
	@Transactional
	public void persistNewTemplate() {
		WordTemplate<?> template = newTemplate();
		repository.persist(template);
		Template<?> persisted = repository.getTemplate("test3", "de");
		assertTrue(isVersionIncontext(persisted));
	}

	@Test
	@Transactional
	public void persistTemplateWithFileStore() throws Exception {
		WordTemplate<?> template = newTemplate();

		FileStore fileStore = mock(FileStore.class);
		File file = File.createTempFile("content", "docx");
		OutputStream out = FileUtils.openOutputStream(file);
		when(fileStore.getOutStream(anyString())).thenReturn(out);
		repository.setFileStore(fileStore);

		try {
			repository.persist(template);

			File epected = FileUtils.toFile(getClass()
					.getResource("empty.docx"));
			assertTrue(FileUtils.contentEquals(epected, file));
		} finally {
			file.deleteOnExit();
		}
	}

	@Test
	@Transactional
	public void persistOfLocked() throws Exception {
		Template<?> template1 = unlockedTemplate();
		WordTemplate<?> template2 = unlockedTemplate();

		template1 = repository.lockForEdit(template1,
				new User("user1", "user1"));
		byte[] content = getContent("empty.docx");
		template2.getPersistentData().setContent(content);
		try {
			repository.persist(template2);
			fail("Exception expected");
		} catch (LockingException e) {
			assertTrue(isVersionIncontext(template1));
		}
	}

	@Test
	@Transactional
	public void changeContent() throws Exception {
		WordTemplate<?> template = unlockedTemplate();

		byte[] content = getContent("empty.docx");
		User user = new User("new_editor", "New Editor");
		template.update(new ByteArrayInputStream(content), user);

		Template<?> peristed = repository.persist(template);
		assertTrue(isVersionIncontext(peristed));
	}

	@Test
	@Transactional
	public void updateTemplateWithFileStore() throws Exception {

		FileStore fileStore = mock(FileStore.class);
		File file = File.createTempFile("content", "docx");
		OutputStream out = FileUtils.openOutputStream(file);
		when(fileStore.getOutStream(anyString())).thenReturn(out);
		repository.setFileStore(fileStore);

		WordTemplate<?> template = unlockedTemplate();
		template.getPersistentData().setContent(getContent("empty.docx"));
		try {
			repository.persist(template);
			File expected = FileUtils.toFile(getClass().getResource(
					"empty.docx"));
			assertTrue(FileUtils.contentEquals(expected, file));
		} finally {
			file.deleteOnExit();
		}
	}

	@Test
	@Transactional
	public void deleteTemplate() {
		WordTemplate<?> template = unlockedTemplate();
		repository.delete(template);
		assertFalse(isVersionIncontext(template));
	}

	@Test
	@Transactional
	public void deleteLockedTemplate() {
		WordTemplate<?> template = lockedTemplate();
		try {
			repository.delete(template);
			fail("Exception expected. Locked templates must not be deleted");
		} catch (LockingException e) {
			assertTrue(isVersionIncontext(template));
		}
	}

	@Test
	@Transactional
	public void delteTemplateWithFileStore() throws Exception {

		FileStore fileStore = mock(FileStore.class);
		repository.setFileStore(fileStore);

		WordTemplate<?> template = unlockedTemplate();
		repository.delete(template);
		verify(fileStore, times(1)).deleteFile(anyString());
		verify(fileStore, times(1)).deleteFile(
				template.getPersistentData().getTemplateFileName());
		assertFalse("template must not be in persistent context",
				isVersionIncontext(template));
	}

	@Test
	@Transactional
	public void errorsWithFileStore() throws Exception {
		WordTemplate<?> template = unlockedTemplate();

		FileStore fileStore = mock(FileStore.class);
		repository.setFileStore(fileStore);

		EntityManager entityManager = mock(EntityManager.class);
		when(entityManager.merge(any())).thenThrow(new PersistenceException());
		repository.em = entityManager;
		try {
			repository.persist(template);
			fail("Exception expected");
		} catch (Exception e) {
			verifyZeroInteractions(fileStore);
		}
	}

	private WordTemplate<?> newTemplate() {
		try {
			PersistentTemplate templateData = new PersistentTemplate();
			templateData.setDocumentName("test3");
			templateData.setLanguage("de");
			templateData.setContent(getContent("empty.docx"));
			templateData.setCreatedAt(new Date());
			templateData.setEditedAt(new Date());
			templateData.setEditor(new User("user", "user"));

			WordTemplate<?> template = new WordTemplate<Object>(templateData,
					modelService, formatterFactory);
			return template;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns a detached locked template
	 */

	private WordTemplate<?> lockedTemplate() {
		PersistentTemplate template = getTemplateInContext(LOCKED_TEMPLATE);
		entityManager.detach(template);
		return new WordTemplate<Object>(template, modelService,
				formatterFactory);
	}

	/**
	 * Returns a detached unlocked template
	 */
	private WordTemplate<?> unlockedTemplate() {
		PersistentTemplate template = getTemplateInContext(UNLOCKED_TEMPLATE);
		entityManager.detach(template);
		return new WordTemplate<Object>(template, modelService,
				formatterFactory);
	}

	private byte[] getContent(String file) throws IOException {
		InputStream in = null;
		try {
			in = getClass().getResourceAsStream(file);
			return IOUtils.toByteArray(in);

		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	PersistentTemplate getTemplateInContext(long id) {
		return entityManager.find(PersistentTemplate.class, id);

	}

	private boolean isVersionIncontext(Template<?> template) {
		WordTemplate<?> wordTemplate = (WordTemplate<?>) template;
		PersistentTemplate inContext = getTemplateInContext(wordTemplate
				.getPersistentData().getId());
		if (inContext == null) {
			return false;
		}
		return inContext.getVersion() == wordTemplate.getPersistentData()
				.getVersion();
	}

}
