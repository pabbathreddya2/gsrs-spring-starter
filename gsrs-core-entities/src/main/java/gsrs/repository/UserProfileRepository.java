package gsrs.repository;

import ix.core.models.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.stream.Stream;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    UserProfile findByUser_Username(String username);

    UserProfile findByKey(String key);

    @Query("select e from UserProfile e")
    Stream<UserProfile> streamAll();
}
