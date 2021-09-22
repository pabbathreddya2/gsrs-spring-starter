package gsrs.repository;

import ix.core.models.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    @Deprecated
    UserProfile findByUser_Username(String username);
    
    UserProfile findByUser_UsernameIgnoreCase(String username);

    UserProfile findByKey(String key);

    @Query("select e from UserProfile e")
    Stream<UserProfile> streamAll();

    @Query("select e.user.username as username, e.key from UserProfile e")
    Stream<UserTokenInfo> streamAllTokenInfo();

    @Query("select e.user.username as username, e.user.email as email, e.user.created," +
            "e.user.modified, e.id, e.active from UserProfile e")
    List<UserProfileSummary> listSummary();

    interface UserTokenInfo{
         String getUsername();
         String getKey();
    }

    interface UserProfileSummary{
        String getUsername();
        String getEmail();
        Date getCreated();
        Date getModified();
        Long getId();
        Boolean getActive();
    }
}
