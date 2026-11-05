package jp.rentaldesk.auth;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class StaffUserDetailsService implements UserDetailsService {
    private final StaffRepository repository;
    public StaffUserDetailsService(StaffRepository repository) { this.repository = repository; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var staff = repository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("スタッフが存在しません"));
        return User.withUsername(staff.username()).password(staff.password()).roles("STAFF").build();
    }
}
