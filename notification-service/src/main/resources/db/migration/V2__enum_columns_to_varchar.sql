ALTER TABLE `notifications` MODIFY `type` varchar(32) NOT NULL;
ALTER TABLE `dlq_messages` MODIFY `status` varchar(32) NOT NULL;
