package bg.tuvarna.devicebackend.services;

import bg.tuvarna.devicebackend.models.dtos.RenovationCreateVO;
import bg.tuvarna.devicebackend.models.entities.Device;
import bg.tuvarna.devicebackend.models.entities.Renovation;
import bg.tuvarna.devicebackend.repositories.RenovationRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class RenovationService {
    private final RenovationRepository renovationRepository;
    private final DeviceService deviceService;

    public Renovation save(RenovationCreateVO vo) {
        Device device = deviceService.isDeviceExists(vo.deviceSerialNumber());

        Renovation renovation = new Renovation();
        renovation.setDevice(device);
        renovation.setDescription(vo.description());
        renovation.setRenovationDate(vo.renovationDate());

        return renovationRepository.save(renovation);
    }
}