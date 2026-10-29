import com.mathio.lms.DAO.*;
import com.mathio.lms.model.*;
import java.nio.file.Path;
public class InspectDatabase {
 public static void main(String[] args) {
  Environment.load(Path.of(".env"));
  try(Database db=Database.configured()) {
   var tests=db.read(c -> Database.rows(c,"SELECT name FROM sys.databases WHERE name LIKE '%[_]test'"));
   System.out.println("Test databases: "+Json.stringify(tests));
   var actors=db.read(c -> Database.rows(c,"SELECT MIN(id) AS id,role FROM users WHERE active=1 AND deleted=0 GROUP BY role"));
   for(var row:actors) {
    Actor actor=new Actor(((Number)row.get("id")).longValue(),row.get("role").toString(),1);
    var catalog=new CatalogDAO(db);
    System.out.println(actor.getRole()+" catalog courses="+Json.MAPPER.valueToTree(catalog.getCourses(actor)).size()+", lessons="+Json.MAPPER.valueToTree(catalog.getLessons(actor,0)).size()+", exercises="+Json.MAPPER.valueToTree(new QuestionDAO(db).getExercises(actor)).size());
   }
  }
 }
}
