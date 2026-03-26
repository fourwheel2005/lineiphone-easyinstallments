package com.example.lineiphone_easyinstallments.entity;


import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_states")
@Data // สร้าง Getter/Setter ให้อัตโนมัติด้วย Lombok
@NoArgsConstructor // สร้าง Default Constructor
@AllArgsConstructor // สร้าง Constructor แบบรับค่าทุกฟิลด์
@Builder // ช่วยให้สร้าง Object ง่ายขึ้น
public class UserState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_user_id", unique = true, nullable = false)
    private String lineUserId;

    @Column(name = "selected_service")
    private String selectedService;

    @Column(name = "current_state", nullable = false)
    private String currentState;

    @Column(name = "previous_state")
    private String previousState;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "device_model")
    private String deviceModel;

    @PrePersist
    @PreUpdate
    public void updateTimestamp() {
        this.lastUpdated = LocalDateTime.now();
    }
}
