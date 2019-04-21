/*
 * The MIT License
 *
 * Copyright 2015 Antonio Rabelo.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.tennaito.rsql.jpa;


import com.github.tennaito.rsql.builder.BuilderTools;
import com.github.tennaito.rsql.jpa.entity.*;
import com.github.tennaito.rsql.misc.SimpleMapper;
import com.github.tennaito.rsql.parser.ast.ComparisonOperatorProxy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.junit.Ignore;
import com.github.tennaito.rsql.jpa.entity.Person;
import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.ast.AbstractNode;
import cz.jirutka.rsql.parser.ast.ComparisonNode;
import cz.jirutka.rsql.parser.ast.ComparisonOperator;
import cz.jirutka.rsql.parser.ast.LogicalNode;
import cz.jirutka.rsql.parser.ast.LogicalOperator;
import cz.jirutka.rsql.parser.ast.Node;
import cz.jirutka.rsql.parser.ast.RSQLVisitor;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.fail;

/**
 * @author AntonioRabelo
 */
@RunWith(Parameterized.class)
public class JpaVisitorTest {

    @Parameterized.Parameters
	public static List<EntityManager[]> data() {
        final TestEntityManagerBuilder testEntityManagerBuilder = new TestEntityManagerBuilder();
        final EntityManager eclipseEntityManager = testEntityManagerBuilder.buildEntityManager("persistenceUnit-eclipse");
        initialize(eclipseEntityManager);
		final EntityManager hibernateEntityManager = testEntityManagerBuilder.buildEntityManager("persistenceUnit-hibernate");
		initialize(hibernateEntityManager);
		return Arrays.asList(new EntityManager[]{eclipseEntityManager}, new EntityManager[]{ hibernateEntityManager});
	}

	private final static XorNode xorNode = new XorNode(new ArrayList<>());

    private final EntityManager entityManager;

	public JpaVisitorTest(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

    private static void initialize(EntityManager entityManager) {
        entityManager.getTransaction().begin();

        Title title1 = new Title();
        title1.setId(1L);
        title1.setName("Phd");
        entityManager.persist(title1);

        Title title2 = new Title();
        title2.setId(2L);
        title2.setName("Consultant");
        entityManager.persist(title2);

        Set<Title> titles = new HashSet<>();
        titles.add(title1);
        titles.add(title2);

        Person head = new Person();
        head.setId(1L);
        head.setName("Some");
        head.setSurname("One");
        head.setTitles(titles);
        entityManager.persist(head);

        Tag tag = new Tag();
        tag.setId(1L);
        tag.setTag("TestTag");
        entityManager.persist(tag);

        ObjTags tags = new ObjTags();
        tags.setId(1L);
        tags.setTags(Collections.singletonList(tag));
        entityManager.persist(tags);

        Department department = new Department();
        department.setId(1L);
        department.setName("Testing");
        department.setCode("MI-MDW");
        department.setHead(head);
        department.setTags(tags);
        entityManager.persist(department);

        Teacher teacher = new Teacher();
        teacher.setId(23L);
        teacher.setSpecialtyDescription("Maths");
        entityManager.persist(teacher);

        Course c = new Course();
        c.setId(1L);
        c.setCode("MI-MDW");
        c.setActive(true);
        c.setCredits(10);
        c.setName("Testing Course");
        c.setDepartment(department);
        c.setDetails(CourseDetails.of("test"));
        c.getDetails().setTeacher(teacher);
        c.setStartDate(new Date());
        entityManager.persist(c);

		PersonCourse personCourse  = new PersonCourse();
		personCourse.setId(1L);
		personCourse.setCode(c.getCode());
		personCourse.setPersonId(head.getId());
		entityManager.persist(personCourse);

		entityManager.getTransaction().commit();
    }

    @Test
	public void testNestedOneToManyPath() {
		Node rootNode = new RSQLParser().parse("rooms.students.titles.title==Hello");

		RSQLVisitor<CriteriaQuery<Building>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Building.class);
		CriteriaQuery<Building> query = rootNode.accept(visitor, entityManager);

	}

    @Test
    public void testUnknowProperty() {
    	try {
    		Node rootNode = new RSQLParser().parse("invalid==1");
    		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    		List<Course> courses = entityManager.createQuery(query).getResultList();
    		fail();
    	} catch (IllegalArgumentException e) {
    		assertEquals("Unknown property: invalid from entity " + Course.class.getName(), e.getMessage());
    	}
    }

    @Test
    public void testSimpleSelection() {
    	Node rootNode = new RSQLParser().parse("id==1");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals("Testing Course", courses.get(0).getName());
    }

    @Test
    public void testSimpleSelectionWhenPassingArgumentInTemplate() {
    	Node rootNode = new RSQLParser().parse("id==1");
    	// not a recommended usage
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals("Testing Course", courses.get(0).getName());
    }


    @Test
    public void testNotEqualSelection() {
    	Node rootNode = new RSQLParser().parse("id!=1");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals(0, courses.size());
    }

    @Test
    public void testGreaterThanSelection() {
    	Node rootNode = new RSQLParser().parse("id=gt=1");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals(0, courses.size());
    }

	@Test
	public void testGreaterThanDate() {
		Node rootNode = new RSQLParser().parse("startDate=gt='2001-01-01'");
		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals(1, courses.size());
	}

	@Test
	public void testGreaterThanString() {
		Node rootNode = new RSQLParser().parse("code=gt='ABC'");
		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals(1, courses.size());
	}

	@Test
	public void testGreaterThanNotComparable() {
    	try {
			Node rootNode = new RSQLParser().parse("details.teacher=gt='ABC'");
			RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
			rootNode.accept(visitor, entityManager);
			fail("should have failed since type isn't Comparable");
		} catch (IllegalArgumentException e) {
    		assertEquals("Invalid type for comparison operator: =gt= type: com.github.tennaito.rsql.jpa.entity.Teacher must implement Comparable<Teacher>", e.getMessage());
		}
	}

	@Test
	public void testGreaterThanEqualSelection() {
		Node rootNode = new RSQLParser().parse("id=ge=1");
		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals("Testing Course", courses.get(0).getName());
	}

	@Test
	public void testGreaterThanEqualSelectionForDate() {
		Node rootNode = new RSQLParser().parse("startDate=ge='2016-01-01'");
		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals("Testing Course", courses.get(0).getName());
	}

	@Test
	public void testGreaterThanEqualSelectionForString() {
		Node rootNode = new RSQLParser().parse("code=ge='MI-MDW'");
		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals("Testing Course", courses.get(0).getName());
	}

	@Test
	public void testEqualSelectionForStringInElementCollection() {
		Node rootNode = new RSQLParser().parse("courses==MI-MDW");
		RSQLVisitor<CriteriaQuery<Person>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Person.class);
		CriteriaQuery<Person> query = rootNode.accept(visitor, entityManager);

		List<Person> courses = entityManager.createQuery(query).getResultList();
		assertEquals(1, courses.size());
	}

	@Test
	public void testEqualSelectionForStringInElementCollectionFailed() {
		Node rootNode = new RSQLParser().parse("courses=='DE-MDW'");
		RSQLVisitor<CriteriaQuery<Person>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Person.class);
		CriteriaQuery<Person> query = rootNode.accept(visitor, entityManager);

		List<Person> courses = entityManager.createQuery(query).getResultList();
		assertEquals(0, courses.size());
	}

	@Test
	public void testGreaterThanEqualNotComparable() {
		try {
			Node rootNode = new RSQLParser().parse("details.teacher=ge='ABC'");
			RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
			rootNode.accept(visitor, entityManager);
			fail("should have failed since type isn't Comparable");
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid type for comparison operator: =ge= type: com.github.tennaito.rsql.jpa.entity.Teacher must implement Comparable<Teacher>", e.getMessage());
		}
	}

	@Test
    public void testLessThanSelection() {
    	Node rootNode = new RSQLParser().parse("id=lt=1");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals(0, courses.size());
    }

    @Test
    public void testLessThanEqualSelection() {
    	Node rootNode = new RSQLParser().parse("id=le=1");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals("Testing Course", courses.get(0).getName());
    }

	@Test
	public void testLessThanDate() {
		Node rootNode = new RSQLParser().parse("startDate=lt='2222-02-02'");
		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals(1, courses.size());
	}

	@Test
	public void testLessThanString() {
		Node rootNode = new RSQLParser().parse("code=lt='MI-MDZ'");
		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals(1, courses.size());
	}

	@Test
	public void testLessThanNotComparable() {
		try {
			Node rootNode = new RSQLParser().parse("details.teacher=lt='ABC'");
			RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
			rootNode.accept(visitor, entityManager);
			fail("should have failed since type isn't Comparable");
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid type for comparison operator: =lt= type: com.github.tennaito.rsql.jpa.entity.Teacher must implement Comparable<Teacher>", e.getMessage());
		}
	}

	@Test
	public void testLessThanEqualSelectionForDate() {
		Node rootNode = new RSQLParser().parse("startDate=le='2100-01-01'");
		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals("Testing Course", courses.get(0).getName());
	}

	@Test
	public void testLessThanEqualSelectionForString() {
		Node rootNode = new RSQLParser().parse("code=le='MI-MDW'");
		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals("Testing Course", courses.get(0).getName());
	}

	@Test
	public void testLessThanEqualNotComparable() {
		try {
			Node rootNode = new RSQLParser().parse("details.teacher=le='ABC'");
			RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
			rootNode.accept(visitor, entityManager);
			fail("should have failed since type isn't Comparable");
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid type for comparison operator: =le= type: com.github.tennaito.rsql.jpa.entity.Teacher must implement Comparable<Teacher>", e.getMessage());
		}
	}


	@Test
    public void testInSelection() {
    	Node rootNode = new RSQLParser().parse("id=in=(1,2,3,4)");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals("Testing Course", courses.get(0).getName());
    }

    @Test
    public void testOutSelection() {
    	Node rootNode = new RSQLParser().parse("id=out=(1,2,3,4)");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals(0, courses.size());
    }

    @Test
    public void testLikeSelection() {
    	Node rootNode = new RSQLParser().parse("name==*Course");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals("Testing Course", courses.get(0).getName());
    }

    @Test
    public void testNotLikeSelection() {
    	Node rootNode = new RSQLParser().parse("name!=*Course");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals(0, courses.size());
    }


    @Test
    public void testIsNullSelection() {
    	Node rootNode = new RSQLParser().parse("name==null");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals(0, courses.size());
    }

    @Test
    public void testNotIsNullSelection() {
    	Node rootNode = new RSQLParser().parse("name!=null");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals("Testing Course", courses.get(0).getName());
    }

    @Test
    public void testSetEntity() {
        Node rootNode = new RSQLParser().parse("id==1");
		JpaCriteriaQueryVisitor<Course> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
        CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);
        List<Course> courses = entityManager.createQuery(query).getResultList();
        assertEquals(1, courses.size());
    }

    @Test
	public void testUndefinedComparisonOperator() {
		try {
			ComparisonOperator newOp = new ComparisonOperator("=def=");
			Set<ComparisonOperator> set = new HashSet<>();
			set.add(newOp);
			Node rootNode = new RSQLParser(set).parse("id=def=null");
			RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
			CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);
			List<Course> courses = entityManager.createQuery(query).getResultList();
			fail();
		} catch(Exception e) {
			assertEquals("Unknown operator: =def=", e.getMessage());
		}
	}

	@Test
    public void testDefinedComparisonOperator() {
    	// define the new operator
		ComparisonOperator newOp = new ComparisonOperator("=def=");
		Set<ComparisonOperator> set = new HashSet<>();
		set.add(newOp);
		// execute parser
    	Node rootNode = new RSQLParser(set).parse("id=def=1");

    	JpaCriteriaQueryVisitor<Course> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	createDefOperator(visitor);

    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);
		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals("Testing Course", courses.get(0).getName());
    }

	private void createDefOperator(JpaCriteriaQueryVisitor<Course> visitor) {
		// define new operator resolver
    	PredicateBuilderStrategy predicateStrategy = new PredicateBuilderStrategy() {
			public <T> Predicate createPredicate(Node node, From root, Class<T> entity,
					EntityManager manager, BuilderTools tools)
					throws IllegalArgumentException {
				ComparisonNode comp = ((ComparisonNode)node);
				ComparisonNode def = new ComparisonNode(ComparisonOperatorProxy.EQUAL.getOperator(), comp.getSelector(), comp.getArguments());
				return new PredicateBuilder().createPredicate(def, root, entity, manager, tools);
			}
		};
    	visitor.getBuilderTools().setPredicateBuilder(predicateStrategy);
	}

    @Test
    public void testAssociationSelection() {
    	Node rootNode = new RSQLParser().parse("department.id==1");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals("Testing Course", courses.get(0).getName());
    }

	@Test
	public void testJoinCaching() {
		Node rootNode = new RSQLParser().parse("department.id==1,department.code==MI-MDW");
		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals("Testing Course", courses.get(0).getName());

        int joinCount = ((JpaCriteriaQueryVisitor)visitor).getPredicateVisitor().getPredicateBuilder().getJoinCount();
        assertEquals(1,joinCount);
    }

	@Test
	public void testAssociationAliasSelection() {
		Node rootNode = new RSQLParser().parse("dept.id==1");
		JpaCriteriaQueryVisitor<Course> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		// add to SimpleMapper
		assertNotNull(((SimpleMapper)visitor.getBuilderTools().getPropertiesMapper()).getMapping());
		((SimpleMapper)visitor.getBuilderTools().getPropertiesMapper()).addMapping(Course.class, new HashMap<>());
		((SimpleMapper)visitor.getBuilderTools().getPropertiesMapper()).addMapping(Course.class, "dept", "department");

		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);
		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals("Testing Course", courses.get(0).getName());

		((SimpleMapper)visitor.getBuilderTools().getPropertiesMapper()).setMapping(null);
		assertNull(((SimpleMapper)visitor.getBuilderTools().getPropertiesMapper()).getMapping());
	}

	@Test
	public void testAssociationAliasSelectionWithAssociationAlias() {
		Node rootNode = new RSQLParser().parse("dept_id==1");
		JpaCriteriaQueryVisitor<Course> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		// add to SimpleMapper
		assertNotNull(((SimpleMapper)visitor.getBuilderTools().getPropertiesMapper()).getMapping());
		((SimpleMapper)visitor.getBuilderTools().getPropertiesMapper()).addMapping(Course.class, new HashMap<>());
		((SimpleMapper)visitor.getBuilderTools().getPropertiesMapper()).addMapping(Course.class, "dept_id", "department.id");

		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);
		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals("Testing Course", courses.get(0).getName());

		((SimpleMapper)visitor.getBuilderTools().getPropertiesMapper()).setMapping(null);
		assertNull(((SimpleMapper)visitor.getBuilderTools().getPropertiesMapper()).getMapping());
	}

	@Test
	public void testChildAssociationAliasMapping() {
		Node rootNode = new RSQLParser().parse("c_code==MI-MDW; dep.d_code==MI-MDW");
		JpaCriteriaQueryVisitor<Course> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		// add to SimpleMapper
		SimpleMapper mapper = new SimpleMapper();
		mapper.addMapping(Course.class, new HashMap<>());
		mapper.addMapping(Course.class, "c_code", "code");
		mapper.addMapping(Course.class, "dep", "department");
		mapper.addMapping(Department.class, new HashMap<>());
		mapper.addMapping(Department.class, "d_code", "code");
		visitor.getBuilderTools().setPropertiesMapper(mapper);

		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);
		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals("Testing Course", courses.get(0).getName());
		assertEquals("Testing", courses.get(0).getDepartment().getName());
	}

	@Test
    public void testAndSelection() {
        Node rootNode = new RSQLParser().parse("department.id==1;id==2");
        RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
        CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

        List<Course> courses = entityManager.createQuery(query).getResultList();
        assertEquals(0, courses.size());
    }

	@Test
	public void testBasicSelectionCount() {
		Node rootNode = new RSQLParser().parse("department.id==1");
		JpaCriteriaCountQueryVisitor<Course> visitor = new JpaCriteriaCountQueryVisitor<>(Course.class);
		CriteriaQuery<Long> query = rootNode.accept(visitor, entityManager);

		Long courseCount = entityManager.createQuery(query).getSingleResult();
		assertEquals((Long) 1L, courseCount);
		Root<Course> root = visitor.getRoot();
		assertNotNull(root);
		visitor.setRoot(root);
	}

	@Test
    public void testAndSelectionCount() {
        Node rootNode = new RSQLParser().parse("department.id==1;id==2");
        RSQLVisitor<CriteriaQuery<Long>, EntityManager> visitor = new JpaCriteriaCountQueryVisitor<Course>(Course.class);
        CriteriaQuery<Long> query = rootNode.accept(visitor, entityManager);

        Long courseCount = entityManager.createQuery(query).getSingleResult();
        assertEquals((Long) 0L, courseCount);
    }

    @Test
    public void testOrSelection() {
    	Node rootNode = new RSQLParser().parse("department.id==1,id==2");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals("Testing Course", courses.get(0).getName());
    }

    @Test
    public void testOrSelectionCount() {
        Node rootNode = new RSQLParser().parse("department.id==1,id==2");
        RSQLVisitor<CriteriaQuery<Long>, EntityManager> visitor = new JpaCriteriaCountQueryVisitor<Course>(Course.class);
        CriteriaQuery<Long> query = rootNode.accept(visitor, entityManager);

        Long courseCount = entityManager.createQuery(query).getSingleResult();
        assertEquals((Long) 1L, courseCount);
    }

    @Test
    public void testVariousNodesSelection() {
    	Node rootNode = new RSQLParser().parse("((department.id==1;id==2),id<3);department.id=out=(3,4,5)");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals("Testing Course", courses.get(0).getName());
    }

    @Test
    public void testNavigateThroughCollectionSelection() {
    	Node rootNode = new RSQLParser().parse("department.head.titles.name==Phd");
    	RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

    	List<Course> courses = entityManager.createQuery(query).getResultList();
    	assertEquals("Testing Course", courses.get(0).getName());
    }

    @Test
    public void testUnsupportedNode() {
    	try{
    		new PredicateBuilder().createPredicate(new OtherNode(), null, null, null, null);
    		fail();
    	} catch (IllegalArgumentException e) {
    		assertEquals("Unknown expression type: class com.github.tennaito.rsql.jpa.JpaVisitorTest$OtherNode", e.getMessage());
    	}
    }

    @Test
    public void testSetBuilderTools() {
    	JpaCriteriaQueryVisitor<Course> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
    	visitor.setBuilderTools(null);
    	assertNotNull(visitor.getBuilderTools());

    	visitor.getBuilderTools().setArgumentParser(null);
    	assertNotNull(visitor.getBuilderTools().getArgumentParser());

    	visitor.getBuilderTools().setPropertiesMapper(null);
    	assertNotNull(visitor.getBuilderTools().getPropertiesMapper());

    	visitor.getBuilderTools().setPredicateBuilder(null);
    	assertNull(visitor.getBuilderTools().getPredicateBuilder());
    }

    @Test
    public void testUnsupportedLogicalNode() {
    	try{
    		new PredicateBuilder().createPredicate(JpaVisitorTest.xorNode, null, Course.class, entityManager, null);
    		fail();
    	} catch (IllegalArgumentException e) {
    		assertEquals("Unknown operator: ^", e.getMessage());
    	}
    }

    @Ignore
    public void testPrivateConstructor() throws Exception {
    	Constructor<PredicateBuilder> priv = PredicateBuilder.class.getDeclaredConstructor();
    	// It is really private?
    	assertFalse(priv.isAccessible());
    	priv.setAccessible(true);
    	Object predicateBuilder = priv.newInstance();
    	// When used it returns a instance?
    	assertNotNull(predicateBuilder);
    }

    ////////////////////////// Mocks //////////////////////////

    static class OtherNode extends AbstractNode {

		public <R, A> R accept(RSQLVisitor<R, A> visitor, A param) {
			throw new UnsupportedOperationException();
		}
    }

    static class XorNode extends LogicalNode {

    	final static LogicalOperator XOR = createLogicalOperatorXor();

	    XorNode(List<? extends Node> children) {
	        super(XOR, children);
	    }

	    static void setStaticFinalField(Field field, Object value) throws NoSuchFieldException, IllegalAccessException {
	    	// we mark the field to be public
	    	field.setAccessible(true);
	    	// next we change the modifier in the Field instance to
	    	// not be final anymore, thus tricking reflection into
	    	// letting us modify the static final field
	    	Field modifiersField = Field.class.getDeclaredField("modifiers");
	    	modifiersField.setAccessible(true);
	    	int modifiers = modifiersField.getInt(field);
	    	// blank out the final bit in the modifiers int
	    	modifiers &= ~Modifier.FINAL;
	    	modifiersField.setInt(field, modifiers);
	    	sun.reflect.FieldAccessor fa = sun.reflect.ReflectionFactory.getReflectionFactory().newFieldAccessor(field, false);
	    	fa.set(null, value);
	    }

		private static LogicalOperator createLogicalOperatorXor() {
			LogicalOperator xor = null;
			try {
				Constructor<LogicalOperator> cstr = LogicalOperator.class.getDeclaredConstructor(String.class, int.class, String.class);
				sun.reflect.ReflectionFactory factory = sun.reflect.ReflectionFactory.getReflectionFactory();
				xor = (LogicalOperator) factory.newConstructorAccessor(cstr).newInstance(new Object[]{"XOR", 2, "^"});

				Field ordinalField = Enum.class.getDeclaredField("ordinal");
			    ordinalField.setAccessible(true);

				LogicalOperator[] values = LogicalOperator.values();
				Field valuesField = LogicalOperator.class.getDeclaredField("ENUM$VALUES");
				valuesField.setAccessible(true);
				LogicalOperator[] newValues = Arrays.copyOf(values, values.length + 1);
				newValues[newValues.length - 1] = xor;
				setStaticFinalField(valuesField, newValues);
				int ordinal = newValues.length - 1;
				ordinalField.set(xor, ordinal);
			} catch (ReflectiveOperationException e) {
				// do nothing
				e.printStackTrace();
			}
			return xor;
		}

		@Override
		public LogicalNode withChildren(List<? extends Node> children) {
			return new XorNode(children);
		}

		public <R, A> R accept(RSQLVisitor<R, A> visitor, A param) {
			throw new UnsupportedOperationException();
		}
    }

    @Test
    public void testUndefinedRootForPredicate() {
    	try {
        	Node rootNode = new RSQLParser().parse("id==1");
        	RSQLVisitor<Predicate, EntityManager> visitor = new JpaPredicateVisitor<>(Course.class);
        	Predicate query = rootNode.accept(visitor, entityManager);
    	} catch (IllegalArgumentException e) {
    		assertEquals("From root node was undefined.", e.getMessage());
    	}
    }

	@Test
	public void testSelectionUsingEmbeddedField() {
		Node rootNode = new RSQLParser().parse("details.description==test");
		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals("Testing Course", courses.get(0).getName());
	}

	@Test
	public void testSelectionUsingEmbeddedAssociationField() {
		Node rootNode = new RSQLParser().parse("details.teacher.specialtyDescription==Maths");
		RSQLVisitor<CriteriaQuery<Course>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Course.class);
		CriteriaQuery<Course> query = rootNode.accept(visitor, entityManager);

		List<Course> courses = entityManager.createQuery(query).getResultList();
		assertEquals("Testing Course", courses.get(0).getName());
	}

	@Test
	public void testNestedSelection() {
		Node rootNode = new RSQLParser().parse("tags.tags.tag=in=(TestTag)");
		RSQLVisitor<CriteriaQuery<Department>, EntityManager> visitor = new JpaCriteriaQueryVisitor<>(Department.class);
		CriteriaQuery<Department> query = rootNode.accept(visitor, entityManager);

		List<Department> departments = entityManager.createQuery(query).getResultList();
		assertEquals("Testing", departments.get(0).getName());
	}
	
	@Test
    public void testSelectionUsingJoinByAlias() {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Person> query = builder.createQuery(Person.class);

        Root<Person> personRoot = query.from(Person.class);
        Join<Person, Title> personTitleJoin = personRoot.join("titles", JoinType.LEFT);
        personTitleJoin.alias("title");

        Node rootNode = new RSQLParser().parse("title.name==Student");
        JpaPredicateVisitor<Course> visitor = new JpaPredicateVisitor<>(Course.class);
        visitor.defineRoot(personRoot);
        Predicate where = rootNode.accept(visitor, entityManager);
    }
}
