package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mathio.lms.DAO.*;
import com.mathio.lms.model.*;
import java.sql.Connection;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class WorkflowValidationTest {
    @Test
    void newExamDefaultsToDraftAndPrivateResults() throws Exception {
        Connection c=mock(Connection.class);DataSource ds=mock(DataSource.class);when(ds.getConnection()).thenReturn(c);
        QuestionDAO questions=mock(QuestionDAO.class);
        var question=ServiceTest.question();
        when(questions.saveQuestion(any(),any(),any())).thenReturn(1L);
        when(questions.getQuestionById(any(),any(),eq(1L),eq(true))).thenReturn(question);
        try(var db=new Database(ds);var sql=mockStatic(Database.class)) {
            sql.when(()->Database.rows(any(),anyString(),any(Object[].class))).thenReturn(List.of());
            sql.when(()->Database.one(any(),anyString(),any(Object[].class))).thenAnswer(inv->{String query=inv.getArgument(1);if(query.contains("SELECT e.id FROM exams"))return Map.of("id",9L);return Map.of("id",9L,"matrix","{}","title","Draft","published",false,"resultsPublished",false);});
            sql.when(()->Database.insert(any(),anyString(),any(Object[].class))).thenAnswer(inv->{String query=inv.getArgument(1);Object[] args=(Object[])inv.getRawArguments()[2];assertTrue(query.contains("results_published"));assertTrue(query.contains("?,?,?,?,?,?,?,0"));assertEquals(false,args[5]);return 9L;});
            var input=Json.object().put("id",-1).put("title","Draft").put("chapter","Chapter").put("topic","Topic");
            input.set("matrix",Json.parse("{\"KNOWLEDGE\":{\"MCQ\":1,\"ESSAY\":0},\"UNDERSTANDING\":{\"MCQ\":0,\"ESSAY\":0},\"APPLICATION\":{\"MCQ\":0,\"ESSAY\":0}}"));
            input.set("questions",Json.array().add(question));
            var service=new ExamService(db,questions);
            assertDoesNotThrow(()->service.saveExam(new Actor(2,"TEACHER",1),input));
            input.put("resultsPublished",true);
            assertDoesNotThrow(()->service.saveExam(new Actor(2,"TEACHER",1),input));
            sql.verify(()->Database.update(any(),contains("SET results_published"),any(Object[].class)),never());
        }
    }

    @Test
    void catalogRequiresAdminAndRejectsDeletionOfReferencedTopics() throws Exception {
        Connection c=mock(Connection.class);DataSource ds=mock(DataSource.class);when(ds.getConnection()).thenReturn(c);
        try(var db=new Database(ds);var sql=mockStatic(Database.class)) {
            var catalog=new CatalogDAO(db);
            assertEquals(403,assertThrows(ApiException.class,()->catalog.saveCatalog(new Actor(2,"TEACHER",1),true,1,Json.object(),true)).status);
            sql.when(()->Database.one(any(),anyString(),any(Object[].class))).thenReturn(Map.of("id",1L,"title","Topic","chapter_id",2L));
            sql.when(()->Database.rows(any(),anyString(),any(Object[].class))).thenReturn(List.of(Map.of("id",10L)));
            assertEquals(409,assertThrows(ApiException.class,()->catalog.saveCatalog(new Actor(3,"ADMIN",1),true,1,Json.object(),true)).status);
            sql.verify(()->Database.update(any(),startsWith("DELETE"),any(Object[].class)),never());
        }
    }

    @Test
    void filtersAreBoundBeforePaginationAndRetainScopedSql() throws Exception {
        Connection c=mock(Connection.class);DataSource ds=mock(DataSource.class);when(ds.getConnection()).thenReturn(c);
        try(var db=new Database(ds);var sql=mockStatic(Database.class)) {
            sql.when(()->Database.rows(any(),anyString(),any(Object[].class))).thenAnswer(inv->{String query=inv.getArgument(1);Object[] args=(Object[])inv.getRawArguments()[2];assertEquals(query.chars().filter(ch->ch=='?').count(),args.length);assertEquals(20,args[args.length-2]);assertEquals(21,args[args.length-1]);assertFalse(query.contains("needle"));assertTrue(Arrays.asList(args).contains("needle"));return List.of();});
            Actor teacher=new Actor(2,"TEACHER",1);
            new UserDAO(db).getUsers(teacher,20,21,"needle","STUDENT","true");
            new CatalogDAO(db).getCourses(teacher,20,21,"needle","false");
            new CatalogDAO(db).getLessons(teacher,0,20,21,"needle","true","Chapter",8);
            new QuestionDAO(db).getQuestions(teacher,20,21,"needle","Chapter","KNOWLEDGE");
        }
    }
}
