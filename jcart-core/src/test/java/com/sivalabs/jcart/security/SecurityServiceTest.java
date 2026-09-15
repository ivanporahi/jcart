package com.sivalabs.jcart.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.sivalabs.jcart.AbstractCoreIntegrationTest;
import com.sivalabs.jcart.JCartException;
import com.sivalabs.jcart.entities.Permission;
import com.sivalabs.jcart.entities.Role;
import com.sivalabs.jcart.entities.User;

@Transactional
public class SecurityServiceTest extends AbstractCoreIntegrationTest
{
	@Autowired SecurityService securityService;

	@Test
	public void seedPermissionsRolesAndUsers()
	{
		assertEquals(9, securityService.getAllPermissions().size());
		assertEquals(4, securityService.getAllRoles().size());
		assertEquals(5, securityService.getAllUsers().size());
	}

	@Test
	public void findUserByEmailLoadsRolesAndPermissions()
	{
		User superAdmin = securityService.findUserByEmail("superadmin@gmail.com");
		assertNotNull(superAdmin);
		assertEquals("Super Admin", superAdmin.getName());
		assertEquals(1, superAdmin.getRoles().size());
		assertEquals("ROLE_SUPER_ADMIN", superAdmin.getRoles().get(0).getName());
		assertEquals(9, superAdmin.getRoles().get(0).getPermissions().size());

		User admin = securityService.findUserByEmail("admin@gmail.com");
		Set<String> perms = permissionNames(admin);
		assertEquals(new HashSet<String>(Arrays.asList("MANAGE_CATEGORIES", "MANAGE_PRODUCTS", "MANAGE_ORDERS",
				"MANAGE_CUSTOMERS", "MANAGE_PAYMENT_SYSTEMS", "MANAGE_SETTINGS")), perms);

		User siva = securityService.findUserByEmail("siva@gmail.com");
		assertEquals(2, siva.getRoles().size());

		User user = securityService.findUserByEmail("user@gmail.com");
		assertEquals(1, user.getRoles().size());
		assertEquals("ROLE_USER", user.getRoles().get(0).getName());
		assertTrue(permissionNames(user).isEmpty());

		assertNull(securityService.findUserByEmail("nobody@gmail.com"));
	}

	@Test
	public void passwordResetTokenLifecycle()
	{
		String email = "admin@gmail.com";
		String token = securityService.resetPassword(email);
		assertNotNull(token);
		assertEquals(36, token.length());
		assertTrue(securityService.verifyPasswordResetToken(email, token));
		assertFalse(securityService.verifyPasswordResetToken(email, "wrong-token"));
		assertFalse(securityService.verifyPasswordResetToken(email, ""));
		assertFalse(securityService.verifyPasswordResetToken(email, null));

		securityService.updatePassword(email, token, "new-encoded-password");
		assertEquals("new-encoded-password", securityService.findUserByEmail(email).getPassword());
		assertNull(securityService.findUserByEmail(email).getPasswordResetToken());
		assertFalse(securityService.verifyPasswordResetToken(email, token));
	}

	@Test
	public void passwordResetForUnknownEmailThrows()
	{
		try
		{
			securityService.resetPassword("nobody@gmail.com");
			fail();
		}
		catch (JCartException e)
		{
			assertEquals("Invalid email address", e.getMessage());
		}
		try
		{
			securityService.updatePassword("nobody@gmail.com", "t", "p");
			fail();
		}
		catch (JCartException e)
		{
			assertEquals("Invalid email address", e.getMessage());
		}
	}

	@Test
	public void updatePasswordWithWrongTokenThrows()
	{
		securityService.resetPassword("admin@gmail.com");
		try
		{
			securityService.updatePassword("admin@gmail.com", "bad", "p");
			fail();
		}
		catch (JCartException e)
		{
			assertEquals("Invalid password reset token", e.getMessage());
		}
	}

	@Test
	public void createRoleResolvesPermissionsByIdAndRejectsDuplicates()
	{
		Role role = new Role();
		role.setName("ROLE_TESTER");
		role.setDescription("test role");
		List<Permission> perms = new ArrayList<Permission>();
		Permission p1 = new Permission();
		p1.setId(1);
		Permission pNull = new Permission(); // id null -> ignored
		perms.add(p1);
		perms.add(pNull);
		role.setPermissions(perms);

		Role saved = securityService.createRole(role);
		assertNotNull(saved.getId());
		assertEquals(1, saved.getPermissions().size());
		assertEquals("MANAGE_CATEGORIES", saved.getPermissions().get(0).getName());
		assertEquals(5, securityService.getAllRoles().size());

		Role dup = new Role();
		dup.setName("ROLE_TESTER");
		try
		{
			securityService.createRole(dup);
			fail();
		}
		catch (JCartException e)
		{
			assertEquals("Role ROLE_TESTER already exist", e.getMessage());
		}
	}

	@Test
	public void updateRoleReplacesPermissions()
	{
		Role cms = securityService.getRoleByName("ROLE_CMS_ADMIN");
		assertEquals(2, cms.getPermissions().size());
		Role update = new Role();
		update.setId(cms.getId());
		update.setDescription("cms updated");
		Permission p = new Permission();
		p.setId(3);
		update.setPermissions(Arrays.asList(p));
		Role updated = securityService.updateRole(update);
		assertEquals("cms updated", updated.getDescription());
		assertEquals(1, updated.getPermissions().size());
		assertEquals("MANAGE_ORDERS", updated.getPermissions().get(0).getName());
	}

	@Test
	public void createUserResolvesRolesAndRejectsDuplicateEmail()
	{
		User u = new User();
		u.setName("Tester");
		u.setEmail("tester@gmail.com");
		u.setPassword("encoded");
		Role r = new Role();
		r.setId(2);
		u.setRoles(Arrays.asList(r));
		User saved = securityService.createUser(u);
		assertNotNull(saved.getId());
		assertEquals("ROLE_ADMIN", saved.getRoles().get(0).getName());
		assertEquals(6, securityService.getAllUsers().size());

		User dup = new User();
		dup.setEmail("tester@gmail.com");
		try
		{
			securityService.createUser(dup);
			fail();
		}
		catch (JCartException e)
		{
			assertEquals("Email tester@gmail.com already in use", e.getMessage());
		}
	}

	@Test
	public void updateUserReplacesRoles()
	{
		User user = securityService.findUserByEmail("user@gmail.com");
		User update = new User();
		update.setId(user.getId());
		Role r1 = new Role();
		r1.setId(1);
		Role r3 = new Role();
		r3.setId(3);
		update.setRoles(Arrays.asList(r1, r3));
		User updated = securityService.updateUser(update);
		assertEquals(2, updated.getRoles().size());
		assertEquals("DemoUser", updated.getName());
	}

	@Test(expected = JCartException.class)
	public void updateUnknownUserThrows()
	{
		User update = new User();
		update.setId(999999);
		securityService.updateUser(update);
	}

	private Set<String> permissionNames(User user)
	{
		Set<String> perms = new HashSet<String>();
		for (Role role : user.getRoles())
		{
			for (Permission permission : role.getPermissions())
			{
				perms.add(permission.getName());
			}
		}
		return perms;
	}
}
