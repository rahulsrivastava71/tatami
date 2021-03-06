package fr.ippon.tatami.uitest.support;

import Constants;
import User;
import CassandraCounterRepository;
import CassandraUserRepository;

@Singleton
public class AccountUtils {

    CassandraUserRepository getUserRepository() {
        def keyspaceOperator = CassandraAccessUtils.instance.keyspaceOperator
        def em = CassandraAccessUtils.instance.entityManager
        def counterRepository = new CassandraCounterRepository(keyspaceOperator:keyspaceOperator)
        def repository = new CassandraUserRepository(keyspaceOperator:keyspaceOperator,em:em,counterRepository:counterRepository)
        return repository
    }
    
    boolean userExists(String login) {
        def userExists = getUserRepository().findUserByLogin(login) != null
        return userExists;
    }
    	
	void assertUserExists(String login) {
		assert userExists(login)
	}
    
    void createUserIfNecessary(String login) {
        if(! userExists(login)) {
            User user = new User()
            user.setLogin(login);
            user.setTheme(Constants.DEFAULT_THEME);
            getUserRepository().createUser(user)
        }
    }
}
