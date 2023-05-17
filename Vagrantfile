Vagrant.configure("2") do |config|
  config.vm.box = "jeffnoxon/ubuntu-20.04-arm64"
  config.vm.provider :parallels do |v|
      v.memory = "4096"
      v.cpus = 4
      v.update_guest_tools = true
  end
  config.vm.synced_folder '.', '/home/vagrant/adyen-java-spring', disabled: false
  config.vm.network :forwarded_port, guest:3000, host: 3000
  config.vm.network :forwarded_port, guest:8080, host: 8080
end