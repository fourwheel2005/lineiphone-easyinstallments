package com.example.lineiphone_easyinstallments.repository;


import com.example.lineiphone_easyinstallments.entity.UserState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserStateRepository extends JpaRepository<UserState, Long> {


    Optional<UserState> findByLineUserId(String lineUserId);

}
